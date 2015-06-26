package controllers

import commercialexpiry.service.{LoggingConsumer, Producer}
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global

class Application extends Controller {

  def produce = Action {
    Producer.run()
    Ok("finished")
  }

  def consume = Action {
    LoggingConsumer.run()
    Ok("finished")
  }
}
