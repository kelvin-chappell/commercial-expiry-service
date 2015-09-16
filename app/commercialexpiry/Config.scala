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

  private def loadConfiguration = {

    val stack = readTag("Stack") getOrElse "flexible"
    val stage = readTag("Stage") getOrElse "DEV"
    val app = readTag("App") getOrElse "commercial-expiry-service"

    val bucketName = s"guconf-$stack"

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

    loadPropertiesFromS3(s"$app/global.properties", props)
    loadPropertiesFromS3(s"$app/$stage.properties", props)

    props.toMap
  }
}
