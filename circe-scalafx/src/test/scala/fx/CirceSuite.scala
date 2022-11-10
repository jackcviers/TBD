package circefx

import munit.fx.ScalaFXSuite
import java.lang.Throwable

import fx.*
import fx.Control
import fx.Control.*
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import Circefx.*

// import io.circe.*, io.circe.generic.auto.*, io.circe.parser.*, io.circe.syntax.*,

import cats.*
import cats.data.*
import cats.implicits.*

class CirceSuite extends ScalaFXSuite, CirceFixtures:
  combined.testFX("Circe FX test") {
    case (x: Int, y: String) =>
      assertEqualsFX(x, 1)
  }

  jsonError.testFX("JSON error") {
    case (errorJson: String) =>
      val test = parsing(errorJson)
      assertEqualsFX(1, 1)
  }
