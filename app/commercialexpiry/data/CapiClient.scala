package commercialexpiry.data

import com.gu.contentapi.client.ContentApiClientLogic
import com.gu.contentapi.client.model.{SearchQuery, SearchResponse}
import commercialexpiry.Config
import commercialexpiry.service.Logger
import dispatch.Http

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

class CapiClient extends ContentApiClientLogic {

  override protected val http: Http = {
    Http configure (_.setAllowPoolingConnections(true))
  }

  override val targetUrl: String = Config.capiUrl

  override val apiKey: String = Config.capiKey
}

object Capi extends Logger {

  private lazy val capiClient = new CapiClient()

  private def fetchTagId(tagType: String, tagSuffix: String): Future[Option[String]] = {
    val query = capiClient.tags.q(tagSuffix).tagType(tagType)
    val eventualResponse = capiClient.getResponse(query)

    eventualResponse onFailure {
      case NonFatal(e) => logger.error("Capi tag lookup failed", e)
    }

    for (response <- eventualResponse) yield {
      val tagIds = for {
        tag <- response.results
        if tag.id endsWith s"/$tagSuffix"
      } yield tag.id
      if (tagIds.size > 1) {
        val results = tagIds.mkString(", ")
        logger.warn(s"Ambiguous $tagType tags for suffix $tagSuffix: $results")
        None
      } else tagIds.headOption
    }
  }

  def fetchSeriesId(suffix: String): Future[Option[String]] = fetchTagId("series", suffix)

  def fetchKeywordId(suffix: String): Future[Option[String]] = fetchTagId("keyword", suffix)

  private def fetchContentIds(q: SearchQuery): Future[Seq[String]] = {

    def fetch(pageIndex: Int, acc: Seq[String]): Future[Seq[String]] = {

      def fetchPage(i: Int): Future[SearchResponse] = {
        val query = q.showTags("tone").pageSize(100).page(i)
        capiClient.getResponse(query)
      }

      val nextPage = fetchPage(pageIndex)

      nextPage onFailure {
        case NonFatal(e) => logger.error("Capi content lookup failed", e)
      }

      nextPage flatMap { response =>
        val pageOfResults = response.results filter {
          _.tags map (_.id) contains "tone/advertisement-features"
        } map (_.id)
        val resultsSoFar = acc ++ pageOfResults
        response.pages match {
          case 0 => Future.successful(Nil)
          case i if i == pageIndex => Future.successful(resultsSoFar)
          case _ => fetch(pageIndex + 1, resultsSoFar)
        }
      }
    }

    fetch(1, Nil)
  }

  def fetchContentIdsByTag(tagId: String): Future[Seq[String]] = {
    fetchContentIds(capiClient.search.tag(tagId))
  }

  def fetchContentIdsBySection(sectionId: String): Future[Seq[String]] = {
    fetchContentIds(capiClient.search.section(sectionId))
  }
}
