package commercialexpiry.service

import java.nio.ByteBuffer

import com.amazonaws.services.kinesis.model.{PutRecordRequest, PutRecordResult}
import commercialexpiry.Config
import commercialexpiry.data._
import commercialexpiry.service.KinesisClient._
import org.joda.time.DateTime
import org.joda.time.DateTime.now
import play.api.Play.current
import play.api.cache.Cache

import scala.concurrent.{ExecutionContext, Future}

object Producer extends Logger {

  private val thresholdKey = "stream.threshold"

  def getUpdates(tags: Seq[PaidForTag],
                 threshold: DateTime,
                 lookUpContentIds: String => Future[Seq[String]])
                (implicit ec: ExecutionContext): Future[Seq[CommercialStatusUpdate]] = {

    def getTagIds(p: LineItem => Boolean): Seq[String] = {
      val (ambiguous, unambiguous) = tags filter {
        _.lineItems.exists(p)
      } partition {
        _.matchingCapiTagIds.size > 1
      }
      if (ambiguous.nonEmpty) {
        logger.info("++++++++++++++++++++++++++++++++ ambiguous: " + ambiguous.size)
      }
      unambiguous flatMap (_.matchingCapiTagIds)
    }

    val tagIdsExpiredRecently = getTagIds { lineItem =>
      lineItem.endTime.exists(_.isAfter(threshold)) && lineItem.endTime.exists(_.isBeforeNow)
    }

    // lifecycle is created > expired > unexpired > expired > ...
    // so need to know what has been updated in last x mins and did not expire in last x mins
    val tagIdsResurrectedRecently = getTagIds { lineItem =>
      lineItem.lastModified.isAfter(threshold) && lineItem.endTime.exists(_.isAfterNow)
      }

    def updates(tagIds: Seq[String], expiryStatus: Boolean): Future[Seq[CommercialStatusUpdate]] = {
      val eventualContentIds = for (tagId <- tagIds) yield lookUpContentIds(tagId)
      val foldedContentIds = Future.fold(eventualContentIds)(Seq.empty[String])(_ ++ _)
      for (contentIds <- foldedContentIds) yield {
        for (contentId <- contentIds.sorted) yield {
          CommercialStatusUpdate(contentId, expired = expiryStatus)
        }
      }
    }

    for {
      expiredUpdates <- updates(tagIdsExpiredRecently, expiryStatus = true)
      resurrectedUpdates <- updates(tagIdsResurrectedRecently, expiryStatus = false)
    } yield {
      expiredUpdates ++ resurrectedUpdates
    }
  }

  def run()(implicit ec: ExecutionContext): Unit = {

    def putOntoStream(update: CommercialStatusUpdate): Future[PutRecordResult] = {
      val status = ByteBuffer.wrap(update.expired.toString.getBytes("UTF-8"))
      val request = new PutRecordRequest()
        .withStreamName(Config.streamName)
        .withPartitionKey(update.contentId)
        .withData(status)
      KinesisClient().asyncPutRecord(request)
    }

    val startTime = now()
    logger.info("Starting streaming...")
    val adFeatureTags = Store.fetchPaidForTags(Config.dfpDataUrl)
    val threshold = Cache.getOrElse[DateTime](thresholdKey)(DateTime.now().minusDays(1))
    logger.info(s"Current threshold is $threshold")

    for {
      tags <- adFeatureTags
    } {
      val eventualUpdates = getUpdates(tags, threshold, Capi.fetchContentIds)
      for (e <- eventualUpdates.failed) logger.error(s"Getting updates failed", e)
      for (updates <- eventualUpdates) {
        logger.info(s"Got ${updates.size} updates")
        val eventualPutResults = for (update <- updates) yield {
          val eventualPutResult = putOntoStream(update)
          for (e <- eventualPutResult.failed) logger.error(s"Streaming update $update failed", e)
          eventualPutResult
        }
        for (putResults <- Future.sequence(eventualPutResults)) {
          logger.info("Streaming successful")
          logger.info(s"Updating threshold to $startTime")
          Cache.set(thresholdKey, startTime)
          }
        }
      }
  }
}
