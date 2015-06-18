package commercialexpiry.data

import play.api.Play.current
import play.api.libs.json._
import play.api.libs.ws.{WS, WSResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

object Store {

  def fetchPaidForTags(url: String)(implicit ec: ExecutionContext): Future[Seq[PaidForTag]] = {

    def fetchData(url: String): Future[JsValue] = {

      // A bad URL throws an exception before the future is created - should have test for this
      def recoverFromPreFutureException(future: => Future[WSResponse]): Future[WSResponse] = {
        Try(future).recover { case NonFatal(e) => Future.failed(e) }.get
      }

      val eventualResponse =
        recoverFromPreFutureException(WS.url(url).withRequestTimeout(2000).get())

      eventualResponse flatMap { response =>
        response.status match {
          case 200 =>
            Future.successful(response.json)
          case _ =>
            val msg = s"Failed to fetch data: ${response.status}: ${response.statusText}"
            Future.failed(new RuntimeException(msg))
        }
      }
    }

    fetchData(url) flatMap { data =>
      val result: JsResult[Seq[PaidForTag]] = (data \ "paidForTags").validate[Seq[PaidForTag]]
      result fold(
        invalid => {
          val (firstPath, firstExceptions) = invalid.head
          val msg = s"Parsing failed: $firstPath: ${firstExceptions.head}"
          Future.failed(new RuntimeException(msg))
        },
        valid =>
          Future.successful(valid)
        )
    }
  }
}
