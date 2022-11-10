package circefx

import fx.*
import fx.Control
import fx.Control.*
import io.circe._
import io.circe.parser._
import cats.*
import cats.data.*
import cats.implicits.*

object Circefx:
  def decodeTest[A](c: io.circe.HCursor)(using d: Decoder[A], cx: Control[DecodingFailure]): A =
    d(c).bind

  def parsing[R, A](s: String)(using cx: Control[R], d: Decoder[A]): A =
    decode(parse(s).bind).bind


  // object extensionTest:
    // extension [E, A](s: ValidatedNel[E, A])
    //   def bind(using Control[NonEmptyList[E]]): A =
    //     s.fold(_.shift, identity)
