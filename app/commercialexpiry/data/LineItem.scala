package commercialexpiry.data

case class LineItem(id: Long, tag: Tag)

sealed trait Tag {val suffix: String}

case class Series(suffix: String) extends Tag

case class Keyword(suffix: String) extends Tag
