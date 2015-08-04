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

  val dfpDataUrl = s"${getRequiredStringProperty("dfp.data.url.prefix")}/all-ad-features-v3.json"
  val dfpDataInitialThreshold = configuration.getIntegerProperty("dfp.data.threshold") getOrElse 24

  val streamName = getRequiredStringProperty("stream.name")

  val pollingInterval = configuration.getIntegerProperty("polling.interval") getOrElse 300
}
