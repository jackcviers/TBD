package circefx

import fx.*
import fx.Control
import io.circe.*
import cats.*
import cats.data.*
import cats.implicits.*

class Circefx

def runProgram =
  def decodeTest[A](c: io.circe.HCursor)(using d: Decoder[A], cx: Control[DecodingFailure]): A =
    d(c).bind

  def parserTest[A](s: String)(using p: Parser, cx: Control[Error], d: Decoder[A]) =
    p.decode(s).bind

    //TODO
  // def parserTest2[A](s: String)(using p: Parser, cx: Control[Error], d: Decoder[A]) =
  //   p.decodeAccumulating(s).bind

  val test: DecodingFailure | String = run(decodeTest[String](???))
