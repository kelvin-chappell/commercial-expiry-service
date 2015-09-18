package commercialexpiry

import java.util.Properties

import com.amazonaws.services.s3.model.GetObjectRequest
import commercialexpiry.service.{AWS, AwsInstanceTags}

import scala.collection.JavaConversions._

object Config extends AwsInstanceTags {

  private val configuration: Map[String, String] = loadConfiguration

  val capiUrl = getRequiredStringProperty("capi.url")
  val capiKey = getRequiredStringProperty("capi.key")

  val dfpDataInitialThreshold = getIntegerProperty("dfp.data.threshold") getOrElse 24
  val pollingInterval = getIntegerProperty("polling.interval") getOrElse 300

  val streamName = getRequiredStringProperty("stream.name")

  def getRequiredStringProperty(key: String): String = {
    configuration.getOrElse(key, {
      throw new IllegalArgumentException(s"Property '$key' not configured")
    })
  }

  def getIntegerProperty(key: String): Option[Int] = {
    configuration.get(key).map(_.toInt)
  }

  object aws {
    lazy val stack = readTag("Stack") getOrElse "flexible"
    lazy val stage = readTag("Stage") getOrElse "DEV"
    lazy val app = readTag("App") getOrElse "commercial-expiry-service"
  }

  object dfp {
    lazy val clientId = getRequiredStringProperty("dfp.client.id")
    lazy val clientSecret = getRequiredStringProperty("dfp.client.secret")
    lazy val refreshToken = getRequiredStringProperty("dfp.refresh.token")
    lazy val appName = getRequiredStringProperty("dfp.app.name")
    lazy val networkCode = getRequiredStringProperty("dfp.network.code")
    val slotTargetKeyId = 174447L
    val seriesTargetKeyId = 180447L
    val keywordTargetKeyId = 177687L
    val adFeatureSlotTargetValueId = 83102154207L
  }

  object logstash {
    val host = "ingest.logs.gutools.co.uk"
    val port = "6379"
  }

  private def loadConfiguration = {

    val bucketName = s"guconf-${aws.stack}"

    def loadPropertiesFromS3(propertiesKey: String, props: Properties): Unit = {
      val s3Properties = AWS.S3Client.getObject(new GetObjectRequest(bucketName, propertiesKey))
      val propertyInputStream = s3Properties.getObjectContent
      try {
        props.load(propertyInputStream)
      } finally {
        try {propertyInputStream.close()} catch {case _: Throwable => /*ignore*/}
      }
    }

    val props = new Properties()

    loadPropertiesFromS3(s"${aws.app}/global.properties", props)
    loadPropertiesFromS3(s"${aws.app}/${aws.stage}.properties", props)

    props.toMap
  }
}
