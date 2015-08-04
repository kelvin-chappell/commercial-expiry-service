package controllers

import javax.inject.Inject

import akka.actor.{ActorSystem, Cancellable}
import commercialexpiry.Config
import commercialexpiry.data.Cache
import commercialexpiry.service.{CommercialStatusUpdate, Logger, LoggingConsumer, Producer}
import play.api.cache.CacheApi
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class Application @Inject()(system: ActorSystem,
                            cacheApi: CacheApi) extends Controller with Logger {

  private val cache = new Cache(cacheApi)

  private def start(): Option[Cancellable] = {
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

  start()

  def showIndex() = Action {
    Ok(views.html.index())
  }

  def healthCheck() = Action {
    val healthy = for {
      schedule <- cache.schedule
      if !schedule.isCancelled
    } yield Ok(s"Current threshold: ${cache.threshold}")
    healthy getOrElse InternalServerError
  }

  def adHocStream(contentId: String, expired: Boolean) = Action.async {
    val update = CommercialStatusUpdate(contentId, expired)
    val eventualResult = Producer.putOntoStream(update)
    for (result <- eventualResult) yield {
      Ok(s"Streamed update $update: ${result.getSequenceNumber}")
    }
  }

  def consume() = Action {
    LoggingConsumer.run()
    Ok("Finished")
  }

  def stop() = Action {
    val msg = cache.schedule match {
      case Some(schedule) =>
        schedule.cancel()
        cache.removeSchedule()
        "Streaming process stopped"
      case None =>
        "No streaming process to stop"
    }
    logger.info(s"Stop request: $msg")
    Ok(msg)
  }

  // this isn't threadsafe! Could end up with an unreachable scheduled process.
  def restart() = Action {
    val msg = start() match {
      case None => "Streaming process already started"
      case Some(s) => "Started streaming process"
    }
    logger.info(s"Start request: $msg")
    Ok(msg)
  }
}
