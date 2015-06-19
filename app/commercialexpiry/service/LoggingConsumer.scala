package commercialexpiry.service

import com.amazonaws.regions.Region.getRegion
import com.amazonaws.regions.Regions.EU_WEST_1
import com.amazonaws.services.kinesis.AmazonKinesisAsyncClient
import com.amazonaws.services.kinesis.model.ShardIteratorType.TRIM_HORIZON
import com.amazonaws.services.kinesis.model.{GetRecordsRequest, GetShardIteratorRequest}
import commercialexpiry.Config
import play.api.Logger

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext

object LoggingConsumer {

  private lazy val client: AmazonKinesisAsyncClient =
    new AmazonKinesisAsyncClient().withRegion(getRegion(EU_WEST_1))

  def fetchFromStream(): Seq[CommercialStatusUpdate] = {
    val shardId = "shardId-000000000000"
    val shardIteratorRequest = new GetShardIteratorRequest()
      .withStreamName(Config.streamName)
      .withShardId(shardId)
      .withShardIteratorType(TRIM_HORIZON)
    val shardIteratorResult = client.getShardIterator(shardIteratorRequest)
    val shardIterator = shardIteratorResult.getShardIterator
    val recordsRequest = new GetRecordsRequest().withShardIterator(shardIterator)
    val recordsResult = client.getRecords(recordsRequest)
    val records = recordsResult.getRecords
    records map { r =>
      val k = r.getPartitionKey
      val v = new String(r.getData.array(), "UTF-8").toBoolean
      CommercialStatusUpdate(k, v)
    }
  }

  def run()(implicit ec: ExecutionContext): Unit = {
    val updatesInStream = fetchFromStream()
    Logger.info("++++++++++++ Current state of stream +++++++++++")
    Logger.info(s"Stream has ${updatesInStream.size} updates")
    for (update <- updatesInStream)
      Logger.info(s"Update in stream: $update")
    Logger.info("++++++++++++++++++++++++++++++++++++++++++++++++")
  }
}
