package commercialexpiry.data

import commercialexpiry.Config
import org.joda.time.DateTime
import play.api.cache.CacheApi

class Cache(cache: CacheApi) {
  private val thresholdKey = "stream.threshold"

  def threshold: DateTime = {
    cache.getOrElse(thresholdKey)(DateTime.now().minusHours(Config.dfpDataInitialThreshold))
  }

  def setThreshold(threshold: DateTime): Unit = cache.set(thresholdKey, threshold)
}
