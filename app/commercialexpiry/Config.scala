package commercialexpiry

import java.util.Properties

import com.amazonaws.services.s3.model.GetObjectRequest
import commercialexpiry.service.{AWS, AwsInstanceTags}
import scala.collection.JavaConversions._

object Config extends AwsInstanceTags {

  private val configuration: Map[String, String] = loadConfiguration

  val capiUrl = getRequiredStringProperty("capi.url")
  val capiKey = getRequiredStringProperty("capi.key")

  val dfpDataUrl = s"${getRequiredStringProperty("dfp.data.url.prefix")}/all-ad-features-v3.json"

  val streamName = getRequiredStringProperty("stream.name")


  def getRequiredStringProperty(key: String): String = {
    configuration.get(key) getOrElse {
      throw new IllegalArgumentException(s"Property '$key' not configured")
    }
  }

  private def loadConfiguration = {

    val stack = readTag("Stack") getOrElse ("flexible")
    val stage = readTag("Stage") getOrElse ("DEV")
    val app = readTag("App") getOrElse ("commercial-expiry-service")

    val bucketName = s"guconf-$stack"

    def loadPropertiesfromS3(propertiesKey: String, props: Properties): Unit = {
      val s3Properties = AWS.S3Client.getObject(new GetObjectRequest(bucketName, propertiesKey))
      val propertyInputStream = s3Properties.getObjectContent
      try {
        props.load(propertyInputStream)
      } finally {
        try { propertyInputStream close } catch { case _: Throwable => /*ignore*/ }
      }
    }

    val props = new Properties()

    loadPropertiesfromS3(s"$app/global.properties", props)
    loadPropertiesfromS3(s"$app/$stage.properties", props)

    props.toMap
  }
}
