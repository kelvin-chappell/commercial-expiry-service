package commercialexpiry.data

import akka.actor.Cancellable
import commercialexpiry.Config
import org.joda.time.DateTime
import play.api.cache.CacheApi

class Cache(cache: CacheApi) {

  private val scheduleKey = "stream.schedule"
  private val thresholdKey = "stream.threshold"

  def schedule: Option[Cancellable] = cache.get[Cancellable](scheduleKey)

  def setSchedule(schedule: Cancellable) = cache.set(scheduleKey, schedule)

  def removeSchedule(): Unit = cache.remove(scheduleKey)

  def threshold: DateTime = {
    cache.getOrElse(thresholdKey)(DateTime.now().minusHours(Config.dfpDataInitialThreshold))
  }

  def setThreshold(threshold: DateTime): Unit = cache.set(thresholdKey, threshold)
}
