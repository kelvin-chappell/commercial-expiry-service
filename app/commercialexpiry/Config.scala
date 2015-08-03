package commercialexpiry

import com.gu.conf.ConfigurationFactory

object Config {
  private val configuration = ConfigurationFactory.getConfiguration("commercial-expiry")

  def getRequiredStringProperty(key: String): String = {
    configuration.getStringProperty(key) getOrElse {
      throw new IllegalArgumentException(s"Property '$key' not configured")
    }
  }

  val capiUrl = getRequiredStringProperty("capi.url")
  val capiKey = getRequiredStringProperty("capi.key")

  val dfpDataUrl = getRequiredStringProperty("dfp.data.url")

  val streamName = getRequiredStringProperty("stream.name")
}