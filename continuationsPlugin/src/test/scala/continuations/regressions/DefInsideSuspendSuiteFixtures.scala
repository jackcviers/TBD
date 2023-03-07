package continuations
package regressions

import munit.FunSuite
import scala.io.Source

trait DefInsideSuspendSuiteFixtures { self: FunSuite & CompilerFixtures =>

  val defInsideSuspendSource = FunFixture[String](
    setup = _ => ResourceUtil.resourceAsString("DefInsideSuspendSource.scala"),
    teardown = _ => ())

  val expectedDefInsideSuspendOutput = FunFixture[String](
    setup = _ => ResourceUtil.resourceAsString("DefInsideSuspendExpected.txt"),
    teardown = _ => ())

  val defInsideSuspendFixtures = FunFixture.map3(
    compilerContextWithContinuationsPlugin,
    defInsideSuspendSource,
    expectedDefInsideSuspendOutput)

}
