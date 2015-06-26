package commercialexpiry.service

import com.amazonaws.services.kinesis.model.ShardIteratorType.TRIM_HORIZON
import com.amazonaws.services.kinesis.model.{GetRecordsRequest, GetShardIteratorRequest}
import commercialexpiry.Config

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext

object LoggingConsumer extends Logger {

  def fetchFromStream(): Seq[CommercialStatusUpdate] = {
    val shardId = "shardId-000000000000"
    val shardIteratorRequest = new GetShardIteratorRequest()
      .withStreamName(Config.streamName)
      .withShardId(shardId)
      .withShardIteratorType(TRIM_HORIZON)
    val shardIteratorResult = KinesisClient().getShardIterator(shardIteratorRequest)
    val shardIterator = shardIteratorResult.getShardIterator
    val recordsRequest = new GetRecordsRequest().withShardIterator(shardIterator)
    val recordsResult = KinesisClient().getRecords(recordsRequest)
    val records = recordsResult.getRecords
    records map { r =>
      val k = r.getPartitionKey
      val v = new String(r.getData.array(), "UTF-8").toBoolean
      CommercialStatusUpdate(k, v)
    }
  }

  def run()(implicit ec: ExecutionContext): Unit = {
    val updatesInStream = fetchFromStream()
    logger.info("++++++++++++ Current state of stream +++++++++++")
    logger.info(s"Stream has ${updatesInStream.size} updates")
    for (update <- updatesInStream)
      logger.info(s"Update in stream: $update")
    logger.info("++++++++++++++++++++++++++++++++++++++++++++++++")
  }
}
