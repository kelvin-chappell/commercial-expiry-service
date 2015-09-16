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

      /*
       * Lifecycle is created > expired > unexpired > expired > ...
       * So need to know what has been updated in last x mins and did not expire in last x
       * mins.
       * This is going to give a lot of false positives but setting items to un-expire
       * more often than strictly necessary shouldn't cause any problems.
       */
      val lineItemsModifiedRecently =
        Dfp.fetchUnexpiredLineItemsModifiedRecently(threshold, session)
      val unexpiredSeriesLineItems =
        LineItemHelper.filterAdFeatureSeriesLineItems(lineItemsModifiedRecently)
      val unexpiredKeywordLineItems =
        LineItemHelper.filterAdFeatureKeywordLineItems(lineItemsModifiedRecently)
      val unexpired = unexpiredSeriesLineItems ++ unexpiredKeywordLineItems
      if (unexpired.nonEmpty) {
        logger.info(
          s"These line items have possibly been resurrected recently: ${mkString(unexpired)}"
        )
      }

      streamLineItems(unexpiredSeriesLineItems, Capi.fetchSeriesId, expiryStatus = false)
      streamLineItems(unexpiredKeywordLineItems, Capi.fetchKeywordId, expiryStatus = false)

      val lineItemsExpiredRecently = Dfp.fetchLineItemsExpiredRecently(threshold, session)
      val expiredSeriesLineItems =
        LineItemHelper.filterAdFeatureSeriesLineItems(lineItemsExpiredRecently)
      val expiredKeywordLineItems =
        LineItemHelper.filterAdFeatureKeywordLineItems(lineItemsExpiredRecently)
      val expired = expiredSeriesLineItems ++ expiredKeywordLineItems
      if (expired.nonEmpty) {
        logger.info(s"These line items have expired recently: ${mkString(expired)}")
      }

      streamLineItems(expiredSeriesLineItems, Capi.fetchSeriesId, expiryStatus = true)
      streamLineItems(expiredKeywordLineItems, Capi.fetchKeywordId, expiryStatus = true)

      cache.setThreshold(startTime)
    }
  }
}
