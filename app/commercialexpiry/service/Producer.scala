package commercialexpiry.service

import java.nio.ByteBuffer

import com.amazonaws.services.kinesis.model.{PutRecordRequest, PutRecordResult}
import commercialexpiry.Config
import commercialexpiry.data._
import commercialexpiry.service.KinesisClient._
import org.joda.time.DateTime.now

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

object Producer extends Logger {

  def putOntoStream(update: CommercialStatusUpdate): Future[PutRecordResult] = {
    val status = ByteBuffer.wrap(update.expired.toString.getBytes("UTF-8"))
    val request = new PutRecordRequest()
      .withStreamName(Config.streamName)
      .withPartitionKey(update.contentId)
      .withData(status)
    KinesisClient().asyncPutRecord(request)
  }

  def run(cache: Cache): Unit = {

    logger.info("Starting streaming run...")
    val threshold = cache.threshold
    logger.info(s"Current threshold is $threshold")

    val startTime = now()

    def mkString[A](as: Seq[A]): String = as mkString ", "

    def streamLineItems(lineItems: Seq[LineItem],
                        fetchTagId: (String) => Future[Option[String]],
                        expiryStatus: Boolean): Unit = {
      for (lineItem <- lineItems) {
        val tagSuffix = lineItem.tag.suffix
        val eventualTagId = fetchTagId(tagSuffix)

        for (NonFatal(e) <- eventualTagId.failed) {
          logger.error(s"Fetching tag ID for $tagSuffix failed: ${e.getMessage}")
        }

        for (maybeTagId <- eventualTagId) {
          for (tagId <- maybeTagId) {
            logger.info(s"$lineItem -> Tag $tagId")
            val eventualContentIds = Capi.fetchContentIds(tagId)

            for (NonFatal(e) <- eventualContentIds.failed) {
              logger.error(s"Fetching content for tag $tagId failed: ${e.getMessage}")
            }

            for (contentIds <- eventualContentIds) {
              logger.info(s"Tag $tagId -> Content ${mkString(contentIds)}")
              for (contentId <- contentIds) {
                val update = CommercialStatusUpdate(contentId, expired = expiryStatus)
                logger.info(s"Streaming $update...")
                val streamingResult: Future[PutRecordResult] = putOntoStream(update)

                for (e <- streamingResult.failed) {
                  logger.error(s"Streaming $update failed: ${e.getMessage}")
                }

                for (result <- streamingResult) {
                  logger.info(s"Streamed $update with seq num ${result.getSequenceNumber}")
                }
              }
            }
          }
        }
      }
    }

    for (session <- Dfp.createSession()) {

      val lineItemsExpiredRecently = Dfp.fetchLineItemsExpiredRecently(threshold, session)

      val expiredSeriesLineItems =
        LineItemHelper.filterAdFeatureSeriesLineItems(lineItemsExpiredRecently, Dfp.seriesTarget)
      streamLineItems(expiredSeriesLineItems, Capi.fetchSeriesId, expiryStatus = true)

      val expiredKeywordLineItems =
        LineItemHelper.filterAdFeatureKeywordLineItems(lineItemsExpiredRecently, Dfp.keywordTarget)
      streamLineItems(expiredKeywordLineItems, Capi.fetchKeywordId, expiryStatus = true)

      val expired = expiredSeriesLineItems ++ expiredKeywordLineItems
      if (expired.nonEmpty) {
        logger.info(s"These line items have expired recently: ${mkString(expired)}")
      }

      cache.setThreshold(startTime)
    }
  }
}
