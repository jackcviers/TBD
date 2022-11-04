package circefx

import _root_.fx.{given, *}
import munit.fx.ScalaFXSuite


trait CirceFixtures { self: ScalaFXSuite =>
  val intFixture = FunFixture(
    setup = _ => 1,
    teardown = _ => ()
  )

  val stringFixture = FunFixture(
    setup = _ => "Esteban", 
    teardown = _ => ()
  )

  val combined: FunFixture[(Int, String)] = 
    FunFixture.map2(intFixture, stringFixture)
}
