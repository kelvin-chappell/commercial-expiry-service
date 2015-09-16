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

  val schedule = scheduler.start()

  def healthCheck() = Action {
    if (schedule.isCancelled) InternalServerError
    else Ok(s"Current threshold: ${cache.threshold}")
  }

  def adHocStream(contentId: String, expired: Boolean) = Action.async {
    val update = CommercialStatusUpdate(contentId, expired)
    val eventualResult = Producer.putOntoStream(update)
    for (result <- eventualResult) yield {
      Ok(s"Streamed update $update: ${result.getSequenceNumber}")
    }
  }
}
