package commercialexpiry.service

import akka.actor.{ActorSystem, Cancellable}
import commercialexpiry.Config
import commercialexpiry.data.Cache

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class Scheduler(system: ActorSystem, cache: Cache) {

  def start()(implicit ec: ExecutionContext): Option[Cancellable] = {
    cache.schedule match {
      case Some(_) => None
      case None =>
        val schedule = system.scheduler.schedule(
          initialDelay = 1.seconds,
          interval = Config.pollingInterval.seconds) {
          Producer.run(cache)
        }
        cache.setSchedule(schedule)
        Some(schedule)
    }
  }

  def stop(): Boolean = {
    cache.schedule match {
      case Some(schedule) =>
        schedule.cancel()
        cache.removeSchedule()
        true
      case None => false
    }
  }
}
