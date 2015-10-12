package commercialexpiry.data

import com.google.api.ads.common.lib.auth.OfflineCredentials
import com.google.api.ads.common.lib.auth.OfflineCredentials.Api
import com.google.api.ads.dfp.axis.factory.DfpServices
import com.google.api.ads.dfp.axis.utils.v201508.StatementBuilder
import com.google.api.ads.dfp.axis.utils.v201508.StatementBuilder._
import com.google.api.ads.dfp.axis.v201508.CustomCriteriaComparisonOperator.IS
import com.google.api.ads.dfp.axis.v201508.{LineItem => DfpLineItem, _}
import com.google.api.ads.dfp.lib.client.DfpSession
import com.google.api.client.auth.oauth2.Credential
import commercialexpiry.Config
import commercialexpiry.Config.dfp._
import commercialexpiry.service.Logger
import org.joda.time.DateTime

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object Dfp extends Logger {

  def createSession(): Option[DfpSession] = {

    def generateCredentials(clientId: String,
                            clientSecret: String,
                            refreshToken: String): Option[Credential] = {
      val triedCredentials = Try(new OfflineCredentials.Builder()
        .forApi(Api.DFP)
        .withClientSecrets(clientId, clientSecret)
        .withRefreshToken(refreshToken)
        .build().generateCredential())
      triedCredentials match {
        case Failure(e) =>
          logger.error(e.getMessage)
          None
        case Success(credentials) =>
          Some(credentials)
      }
    }

    for {
      credentials <- generateCredentials(Config.dfp.clientId,
        Config.dfp.clientSecret,
        Config.dfp.refreshToken)
    } yield {
      new DfpSession.Builder()
        .withOAuth2Credential(credentials)
        .withApplicationName(Config.dfp.appName)
        .withNetworkCode(Config.dfp.networkCode)
        .build()
    }
  }

  private def logMessage(apiException: ApiException): String = {

    def msg(label: String, content: String) =
      if (content.nonEmpty) Some(s"$label: $content") else None

    val msgs = for (err <- apiException.getErrors) yield {
      Seq(
        msg("Error", err.getErrorString),
        msg("trigger", err.getTrigger),
        msg("field path", err.getFieldPath)
      ).flatten mkString "; "
    }
    msgs mkString ", "
  }

  private def fetchLineItems(session: DfpSession,
                             stmtBuilder: StatementBuilder): Seq[DfpLineItem] = {

    val lineItemService = new DfpServices().get(session, classOf[LineItemServiceInterface])

    def fetch(soFar: Seq[DfpLineItem]): Seq[DfpLineItem] = {
      val triedPage = Try(Option(lineItemService.getLineItemsByStatement(stmtBuilder.toStatement)))
      triedPage match {

        case Failure(e) =>
          val msg = e match {
            case ae: ApiException => logMessage(ae)
            case NonFatal(_) => e.getMessage
          }
          logger.error(msg)
          soFar

        case Success(maybePage) =>
          val resultsSoFar = soFar ++
            maybePage.flatMap(page => Option(page.getResults).map(_.toSeq)).getOrElse(Nil)
          if (maybePage.isEmpty || maybePage.exists(resultsSoFar.size >= _.getTotalResultSetSize)) {
            resultsSoFar
          } else {
            stmtBuilder.increaseOffsetBy(SUGGESTED_PAGE_LIMIT)
            fetch(resultsSoFar)
          }
      }
    }

    stmtBuilder.limit(SUGGESTED_PAGE_LIMIT)
    fetch(Nil)
  }

  def fetchTagTargetValue(keyId: Long, valueId: Long): Option[String] = {
    val stmtBuilder = new StatementBuilder()
      .where("customTargetingKeyId = :keyId AND id = :valueId")
      .withBindVariableValue("keyId", keyId)
      .withBindVariableValue("valueId", valueId)

    for {
      session <- createSession()
      targetingService = new DfpServices().get(session, classOf[CustomTargetingServiceInterface])
      tagTarget <- Try(
        targetingService.getCustomTargetingValuesByStatement(stmtBuilder.toStatement)
      )
      match {
        case Failure(e) =>
          val message = e match {
            case ae: ApiException => logMessage(ae)
            case NonFatal(_) => e.getMessage
          }
          logger.error(message)
          None
        case Success(page) => Some(page.getResults.head.getName)
      }
    } yield tagTarget
  }

  def fetchUnexpiredLineItemsModifiedRecently(threshold: DateTime,
                                              session: DfpSession): Seq[DfpLineItem] = {
    val stmtBuilder = new StatementBuilder()
      .where("endDateTime > :now AND lastModifiedDateTime >= :threshold")
      .withBindVariableValue("now", DateTime.now().toString)
      .withBindVariableValue("threshold", threshold.toString)

    fetchLineItems(session, stmtBuilder)
  }

  def fetchLineItemsExpiredRecently(threshold: DateTime,
                                    session: DfpSession): Seq[DfpLineItem] = {
    val stmtBuilder = new StatementBuilder()
      .where("endDateTime >= :threshold AND endDateTime <= :now")
      .withBindVariableValue("threshold", threshold.toString)
      .withBindVariableValue("now", DateTime.now().toString)

    fetchLineItems(session, stmtBuilder)
  }

  def keywordTarget(keywordId: Long): Option[String] = {
    fetchTagTargetValue(Config.dfp.keywordTargetKeyId, keywordId)
  }

  def seriesTarget(seriesId: Long): Option[String] = {
    fetchTagTargetValue(Config.dfp.seriesTargetKeyId, seriesId)
  }
}

object LineItemHelper {

  private def adFeatureTagTarget(lineItem: DfpLineItem,
                                 tagTarget: CustomCriteriaSet => Option[String]): Option[String] = {
    val tagTargets = Option(lineItem.getTargeting.getCustomTargeting) map {
      CustomTargeting.criteriaSets(_) flatMap (tagTarget(_))
    } getOrElse Nil
    tagTargets.headOption
  }

  def filterAdFeatureSeriesLineItems(lineItems: Seq[DfpLineItem],
                                     seriesTarget: Long => Option[String]): Seq[LineItem] = {
    for {
      lineItem <- lineItems
      seriesTarget <- adFeatureTagTarget(
        lineItem,
        criteriaSet =>
          CustomTargeting.adFeatureTagTarget(
            criteriaSet,
            targetKeyId = seriesTargetKeyId,
            targetValue = seriesTarget)
      )
    } yield {
      LineItem(lineItem.getId, Series(seriesTarget))
    }
  }

  def filterAdFeatureKeywordLineItems(lineItems: Seq[DfpLineItem],
                                      keywordTarget: Long => Option[String]): Seq[LineItem] = {
    for {
      lineItem <- lineItems
      keywordTarget <- adFeatureTagTarget(lineItem,
        criteriaSet =>
          CustomTargeting.adFeatureTagTarget(
            criteriaSet,
            targetKeyId = keywordTargetKeyId,
            targetValue = keywordTarget)
      )
    } yield {
      LineItem(lineItem.getId, Keyword(keywordTarget))
    }
  }
}

// see https://developers.google.com/doubleclick-publishers/docs/reference/v201508
// /LineItemService.Targeting
object CustomTargeting {

  def criteriaSets(customTargeting: CustomCriteriaSet): Seq[CustomCriteriaSet] = {
    customTargeting.getChildren collect {
      case criteriaSet: CustomCriteriaSet => criteriaSet
    }
  }

  def criteria(criteriaSet: CustomCriteriaSet): Seq[CustomCriteria] = {
    criteriaSet.getChildren collect {
      case criteria: CustomCriteria => criteria
    }
  }

  def contains(criteriaSet: CustomCriteriaSet)(p: CustomCriteria => Boolean): Boolean = {
    criteria(criteriaSet) exists p
  }

  def find(criteriaSet: CustomCriteriaSet)(p: CustomCriteria => Boolean): Option[CustomCriteria] = {
    criteria(criteriaSet) find p
  }

  def adFeatureTagTarget(criteriaSet: CustomCriteriaSet,
                         targetKeyId: Long,
                         targetValue: Long => Option[String]): Option[String] = {

    val hasAdFeatureSlotTarget = {
      contains(criteriaSet) { criterion =>
        criterion.getKeyId == slotTargetKeyId &&
          criterion.getValueIds.head == adFeatureSlotTargetValueId
      }
    }

    if (hasAdFeatureSlotTarget) {
      for {
        criterion <- find(criteriaSet) { c =>
          c.getOperator == IS && c.getKeyId == targetKeyId
        }
        tagTarget <- targetValue(criterion.getValueIds(0))
      } yield tagTarget
    } else None
  }
}
