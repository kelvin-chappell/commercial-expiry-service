package commercialexpiry.tools

import java.io.File

import commercialexpiry.data.Capi
import commercialexpiry.service.{CommercialStatusUpdate, Producer}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.Source
import scala.util.control.NonFatal

object NginxRedirectExpirer extends App {

  def extractSectionIds(nginxRedirectFile: File): Seq[String] = {
    Source.fromFile(nginxRedirectFile).getLines().filter {
      _.trim.endsWith(" /info/2015/feb/06/paid-content-removal-policy permanent;")
    }.map {
      _.trim.stripPrefix("rewrite ^/")
        .stripSuffix(".* /info/2015/feb/06/paid-content-removal-policy permanent;")
    }.toSeq
  }

  def sections(sectionIds: Seq[String],
               content: String => Future[Seq[String]]): Future[Map[String, Seq[String]]] = {
    val sectionContents = for (sectionId <- sectionIds) yield {
      content(sectionId) map { contentIds =>
        sectionId -> contentIds
      } recover {
        case NonFatal(e) =>
          println(s"*** $sectionId: ${e.getMessage} ***")
          sectionId -> Nil
      }
    }
    Future.fold(sectionContents)(Map.empty[String, Seq[String]]) { (soFar, section) =>
      soFar + section
    }
  }

  private val result = {
    val sectionIds = extractSectionIds(new File("redirects.conf"))
    // todo: sort out calls to Capi not closing properly
    Await.result(sections(sectionIds, Capi.fetchContentIdsBySection), 1.minute)
  }

  println(s"Sections: ${result.size}")
  println(s"Content Items: ${result.values.flatten.size}")
  println()

  for ((sectionId, contentIds) <- result.toList.sortBy { case (key, _) => key }) {
    println(sectionId)
    if (contentIds.isEmpty) println("\t*** NO CONTENT ***")
    for (id <- contentIds.sorted) println(s"\thttp://www.theguardian.com/$id")
    println("============")
  }

  println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++")
  for ((sectionId, contentIds) <- result.toList.sortBy { case (key, _) => key }) {
    for (id <- contentIds.sorted) {
      val streamingResult = Producer.putOntoStream(CommercialStatusUpdate(id, expired = true))
      streamingResult onSuccess {
        case r => println(s"Streaming $id succeeded: seq# ${r.getSequenceNumber}")
      }
      streamingResult onFailure {
        case NonFatal(e) => println(s"Streaming $id failed: ${e.getMessage}")
      }
    }
  }
}
