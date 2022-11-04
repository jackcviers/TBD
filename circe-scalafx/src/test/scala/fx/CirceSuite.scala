package circefx

import munit.fx.ScalaFXSuite

class CirceSuite extends ScalaFXSuite, CirceFixtures:
  combined.testFX("Circe FX test") { case (x: Int, y: String) => assertEqualsFX(x, 1) }
