package commercialexpiry.service

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.regions.Region.getRegion
import com.amazonaws.regions.Regions.EU_WEST_1
import com.amazonaws.services.kinesis.AmazonKinesisAsyncClient
import com.amazonaws.services.kinesis.model.{PutRecordRequest, PutRecordResult}

import scala.concurrent.{Future, Promise}

object KinesisClient {

  implicit class RichKinesisClient(client: AmazonKinesisAsyncClient) {

    def asyncPutRecord(request: PutRecordRequest): Future[PutRecordResult] = {
      val promise = Promise[PutRecordResult]()

      val handler = new AsyncHandler[PutRecordRequest, PutRecordResult] {
        override def onSuccess(request: PutRecordRequest, result: PutRecordResult): Unit =
          promise.success(result)
        override def onError(exception: Exception): Unit =
          promise.failure(exception)
      }

      client.putRecordAsync(request, handler)

      promise.future
    }
  }

  private lazy val client: AmazonKinesisAsyncClient =
    new AmazonKinesisAsyncClient().withRegion(getRegion(EU_WEST_1))

  def apply(): AmazonKinesisAsyncClient = client
}
