package circefx

import fx.*
import fx.Control
import fx.Control.*
import io.circe.*
import cats.*
import cats.data.*
import cats.implicits.*

import Continuation.*

object Circefx:
  def decodeTest[A](c: io.circe.HCursor)(using d: Decoder[A], cx: Control[DecodingFailure]): A =
    d(c).bind

  def parsing[A](s: String)(using p: Parser, cx: Control[Error], d: Decoder[A]): A =
    p.decode(s).bind

  object extensionTest:
    def foldNonEmpty[E, A](x: NonEmptyList[E])(using control: Control[NonEmptyList[E]]): A =
      // summon[Control[NonEmptyList[E]]]
      ???

    extension [E, A](s: ValidatedNel[E, A])
      def bind(using Control[NonEmptyList[E]]): A =
        s.fold(foldNonEmpty, identity)
