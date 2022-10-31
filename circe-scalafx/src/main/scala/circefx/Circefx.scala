package circefx

import fx.*
import fx.Control
import io.circe.*
import cats.*
import cats.data.*
import cats.implicits.*

object implicits:
  extension [A](fa: ValidatedNel[DecodingFailure, A])
    def bind(using fx.Control[NonEmptyList[DecodingFailure]]): A =
      fa.fold(_.shift, identity)

class Circefx

def runProgram =
//   def program[A](c: Json)(using d: Decoder[A], cx: Control[DecodingFailure]): A =
  def program[A](c: io.circe.HCursor)(using d: Decoder[A], cx: Control[DecodingFailure]): A =
    d(c).bind

  // Parsed document
  val test: DecodingFailure | String = run(program[String](???))
