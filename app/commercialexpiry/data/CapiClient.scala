package commercialexpiry.data

import com.gu.contentapi.client.ContentApiClientLogic
import com.gu.contentapi.client.model.SearchResponse
import commercialexpiry.Config
import dispatch.Http
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class CapiClient extends ContentApiClientLogic {

  override protected val http: Http = {
    Http configure (_.setAllowPoolingConnections(true))
  }

  override val targetUrl: String = Config.capiUrl

  override val apiKey: String = Config.capiKey
}

object Capi {

  private lazy val capiClient = new CapiClient()

  def fetchContentIds(tagId: String)(implicit ec: ExecutionContext): Future[Seq[String]] = {

    def fetch(pageIndex: Int, acc: Seq[String]): Future[Seq[String]] = {

      def fetchPage(i: Int): Future[SearchResponse] = {
        val query = capiClient.search.tag(tagId).pageSize(100).page(i)
        capiClient.getResponse(query)
      }

      val nextPage = fetchPage(pageIndex)

      nextPage onFailure {
        case NonFatal(e) => Logger.error("Capi lookup failed", e)
      }

      nextPage flatMap { response =>
        val resultsSoFar = acc ++ response.results.map(_.id)
        response.pages match {
          case 0 => Future.successful(Nil)
          case i if i == pageIndex => Future.successful(resultsSoFar)
          case _ => fetch(pageIndex + 1, resultsSoFar)
        }
      }
    }

    fetch(1, Nil)
  }
}