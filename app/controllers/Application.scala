package controllers

import javax.inject.Inject

import akka.actor.ActorSystem
import commercialexpiry.data.Cache
import commercialexpiry.service._
import play.api.cache.CacheApi
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global

class Application @Inject()(system: ActorSystem,
                            cacheApi: CacheApi) extends Controller with Logger {

  private val cache = new Cache(cacheApi)
  private val scheduler = new Scheduler(system, cache)

  scheduler.start()

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
    val stopped = scheduler.stop()
    val msg = if (stopped) "Streaming process stopped" else "No streaming process to stop"
    logger.info(s"Stop request: $msg")
    Ok(msg)
  }

  // this isn't threadsafe! Could end up with an unreachable scheduled process.
  def restart() = Action {
    val msg = scheduler.start() match {
      case None => "Streaming process already started"
      case Some(s) => "Started streaming process"
    }
    logger.info(s"Start request: $msg")
    Ok(msg)
  }
}
