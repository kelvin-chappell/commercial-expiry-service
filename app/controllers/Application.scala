package controllers

import javax.inject.Inject

import akka.actor.{ActorSystem, Cancellable}
import commercialexpiry.service.{CommercialStatusUpdate, Logger, LoggingConsumer, Producer}
import org.joda.time.DateTime
import play.api.cache.CacheApi
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class Application @Inject()(system: ActorSystem, cache: CacheApi) extends Controller with Logger {

  private val scheduleKey = "streamingSchedule"

  private def start(): Option[Cancellable] = {
    cache.get[Cancellable](scheduleKey) match {
      case Some(_) => None
      case None =>
        val schedule = system.scheduler.schedule(initialDelay = 1.seconds, interval = 30.seconds) {
          Producer.run(cache)
        }
        cache.set(scheduleKey, schedule)
        Some(schedule)
    }
  }

  start()

  def showIndex() = Action {
    Ok(views.html.index())
  }

  def healthCheck() = Action {
    val healthy = for {
      schedule <- cache.get[Cancellable](scheduleKey)
      if !schedule.isCancelled
      threshold <- cache.get[DateTime](Producer.thresholdKey)
    } yield {
        Ok(s"Current threshold: $threshold")
      }
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
    val msg = cache.get[Cancellable](scheduleKey) match {
      case Some(schedule) =>
        schedule.cancel()
        cache.remove(scheduleKey)
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
