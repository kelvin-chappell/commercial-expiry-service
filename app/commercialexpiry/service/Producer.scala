package commercialexpiry.service

import java.nio.ByteBuffer

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.regions.Region.getRegion
import com.amazonaws.regions.Regions.EU_WEST_1
import com.amazonaws.services.kinesis.AmazonKinesisAsyncClient
import com.amazonaws.services.kinesis.model.{PutRecordRequest, PutRecordResult}
import com.gu.contentapi.client.model.SearchResponse
import commercialexpiry.Config
import commercialexpiry.data.{CapiClient, LineItem, PaidForTag, Store}
import org.joda.time.DateTime
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

object Producer {

  implicit class RichKinesisClient(client: AmazonKinesisAsyncClient) {

    def asyncPutRecord(request: PutRecordRequest): Future[PutRecordResult] = {
      val promise = Promise[PutRecordResult]()

      val handler = new AsyncHandler[PutRecordRequest, PutRecordResult] {
        override def onSuccess(request: PutRecordRequest, result: PutRecordResult): Unit =
          promise.complete(Success(result))
        override def onError(exception: Exception): Unit =
          promise.complete(Failure(exception))
      }

      client.putRecordAsync(request, handler)

      promise.future
    }
  }

  private lazy val client: AmazonKinesisAsyncClient =
    new AmazonKinesisAsyncClient().withRegion(getRegion(EU_WEST_1))

  private lazy val capiClient = new CapiClient()

  private def getTagIds(tags: Seq[PaidForTag])(p: LineItem => Boolean)
                       (implicit ec: ExecutionContext): Seq[String] = {
      val expired = tags filter (_.lineItems.exists(p))
      val (ambiguous, unambiguous) = expired partition (_.matchingCapiTagIds.size > 1)
      if (ambiguous.nonEmpty)
        Logger.info("++++++++++++++++++++++++++++++++ ambiguous: " + ambiguous.size)
      unambiguous flatMap (_.matchingCapiTagIds)
  }

  def tagIdsExpiredRecently(tags: Seq[PaidForTag], time: DateTime)
                           (implicit ec: ExecutionContext): Seq[String] = {
    getTagIds(tags) { lineItem =>
      lineItem.endTime.exists(_.isAfter(time)) && lineItem.endTime.exists(_.isBeforeNow)
    }
  }

  def tagIdsResurrectedRecently(tags: Seq[PaidForTag], time: DateTime)
                               (implicit ec: ExecutionContext): Seq[String] = {
    // lifecycle is created > expired > unexpired > expired > ...
    // so need to know what has been updated in last x mins and did not expire in last x mins
    getTagIds(tags) { lineItem =>
      lineItem.lastModified.isAfter(time) && lineItem.endTime.exists(_.isAfterNow)
    }
  }

  def fetchContentIds(tagId: String)(implicit ec: ExecutionContext): Future[Seq[String]] = {

    def fetch(pageIndex: Int, acc: Seq[String]): Future[Seq[String]] = {

      def fetchPage(i: Int): Future[SearchResponse] = {
        val query = capiClient.search.tag(tagId).pageSize(100).page(i)
        capiClient.getResponse(query)
      }

      val nextPage = fetchPage(pageIndex)

      nextPage onFailure {
        case NonFatal(e) => Logger.error("Capi lookup failed", e)
      }

      nextPage flatMap { response =>
        val resultsSoFar = acc ++ response.results.map(_.id)
        response.pages match {
          case 0 => Future.successful(Nil)
          case i if i == pageIndex => Future.successful(resultsSoFar)
          case _ => fetch(pageIndex + 1, resultsSoFar)
        }
      }
    }

    fetch(1, Nil)
  }

  def putOntoStream(update: CommercialStatusUpdate): Future[PutRecordResult] = {
    val status = ByteBuffer.wrap(update.expired.toString.getBytes("UTF-8"))
    val request = new PutRecordRequest()
      .withStreamName(Config.streamName)
      .withPartitionKey(update.contentId)
      .withData(status)
    client.asyncPutRecord(request)
  }

  def run()(implicit ec: ExecutionContext): Unit = {

    def stream(eventualTags: Future[Seq[PaidForTag]], threshold: DateTime): Unit = {

      def stream(tagIds: Seq[String],
                 expiryStatus: Boolean): Future[Seq[Future[PutRecordResult]]] = {
        Future.sequence(tagIds map fetchContentIds) map (_.flatten) map { contentIds =>

          val duplicates = contentIds.groupBy(identity).values.filter(_.size > 1)
          if (duplicates.nonEmpty) {
            Logger.info(s"+++++++++++++++++++++++++++++++++++++++ duplicates! : ${duplicates.size}")
          }

          for (id <- contentIds.sorted) yield {
            val update = CommercialStatusUpdate(id, expiryStatus)
            val result = putOntoStream(update)
            result onFailure {
              case NonFatal(e) => Logger.error(s"Streaming $update failed", e)
            }
            result
          }
        }
      }

      for (tags <- eventualTags) {
        stream(tagIdsExpiredRecently(tags, threshold), expiryStatus = true)
        stream(tagIdsResurrectedRecently(tags, threshold), expiryStatus = false)
      }
      for (NonFatal(e) <- eventualTags.failed) {
        Logger.error("Streaming updates failed", e)
      }
    }

    val threshold = DateTime.now().minusMonths(2)
    val adFeatureTags = Store.fetchPaidForTags(Config.dfpDataUrl)
    stream(adFeatureTags, threshold)
  }
}
