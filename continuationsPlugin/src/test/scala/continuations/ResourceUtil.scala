package continuations

import scala.io.Source

object ResourceUtil:
  def resourceAsString(name: String): String =
    Source.fromResource(name).getLines().mkString("\n")

