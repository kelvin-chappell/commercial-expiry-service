package controllers

import javax.inject.Inject

import akka.actor.{ActorSystem, Cancellable}
import commercialexpiry.service.{Logger, LoggingConsumer, Producer}
import play.api.cache.CacheApi
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class Application @Inject()(system: ActorSystem, cache: CacheApi) extends Controller with Logger {

  def showIndex() = Action {
    Ok(views.html.index())
  }

  def produce() = Action {
    Producer.run(cache)
    Ok("finished")
  }

  def consume() = Action {
    LoggingConsumer.run()
    Ok("finished")
  }

  private val scheduleKey = "streamingSchedule"

  // this isn't threadsafe!
  def start() = Action {
    val msg = cache.get[Cancellable](scheduleKey) match {
      case Some(s) =>
        "Streaming process already started"
      case None =>
        cache.set(scheduleKey,
          system.scheduler.schedule(initialDelay = 1.seconds, interval = 30.seconds) {
            Producer.run(cache)
          })
        "Started streaming process"
    }
    logger.info(s"Start request: $msg")
    Ok(msg)
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
}
