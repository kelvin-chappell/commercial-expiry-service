package controllers

import commercialexpiry.service.Producer
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global

class Application extends Controller {

  def index = Action {
    Producer.run()
    Ok("finished")
  }
}
