package commercialexpiry.data

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.libs.functional.syntax._
import play.api.libs.json._

case class LineItem(id: Long,
                    name: String,
                    endTime: Option[DateTime],
                    status: String,
                    targeting: Targeting,
                    lastModified: DateTime)

object LineItem {

  private val timeFormatter = ISODateTimeFormat.dateTime().withZoneUTC()

  implicit val lineItemReads: Reads[LineItem] = (
    (JsPath \ "id").read[Long] and
      (JsPath \ "name").read[String] and
      (JsPath \ "endTime").readNullable[String].map(_.map(timeFormatter.parseDateTime)) and
      (JsPath \ "status").read[String] and
      (JsPath \ "targeting").read[Targeting] and
      (JsPath \ "lastModified").read[String].map(timeFormatter.parseDateTime)
    )(LineItem.apply _)
}

case class Targeting(customTargetSets: Seq[CustomTargetSet])

object Targeting {

  implicit val targetingReads: Reads[Targeting] =
    (JsPath \ "customTargetSets").read[Seq[CustomTargetSet]].map(Targeting(_))
}

case class CustomTargetSet(op: String, targets: Seq[CustomTarget])

object CustomTargetSet {

  implicit val customTargetSetReads: Reads[CustomTargetSet] = (
    (JsPath \ "op").read[String] and
      (JsPath \ "targets").read[Seq[CustomTarget]]
    )(CustomTargetSet.apply _)
}

case class CustomTarget(name: String, op: String, values: Seq[String])

object CustomTarget {

  implicit val customTargetReads: Reads[CustomTarget] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "op").read[String] and
      (JsPath \ "values").read[Seq[String]]
    )(CustomTarget.apply _)
}

case class PaidForTag(targetedName: String,
                      tagType: String,
                      paidForType: String,
                      matchingCapiTagIds: Seq[String],
                      lineItems: Seq[LineItem])

object PaidForTag {

  implicit val jsonReads: Reads[PaidForTag] = (
    (JsPath \ "targetedName").read[String] and
      (JsPath \ "tagType").read[String] and
      (JsPath \ "paidForType").read[String] and
      (JsPath \ "matchingCapiTagIds").read[Seq[String]] and
      (JsPath \ "lineItems").read[Seq[LineItem]]
    )(PaidForTag.apply _)
}
