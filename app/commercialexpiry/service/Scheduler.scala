package commercialexpiry.service

import akka.actor.{ActorSystem, Cancellable}
import commercialexpiry.Config
import commercialexpiry.data.Cache

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class Scheduler(system: ActorSystem, cache: Cache) {

  def start(): Cancellable = {
    system.scheduler.schedule(
      initialDelay = 1.seconds,
      interval = Config.pollingInterval.seconds) {
      Producer.run(cache)
    }
  }
}
