package continuations
package regressions

import munit.FunSuite

trait SuspendInLambdaSuiteFixtures { self: FunSuite & CompilerFixtures =>

  val suspendInLambdaSource = FunFixture[String](
    setup = _ => ResourceUtil.resourceAsString("SuspendInLambda.scala"),
    teardown = _ => ())

  val expectedSuspendInLambdaOutput = FunFixture[String](
    setup = _ => ResourceUtil.resourceAsString("SuspendInLambdaExpected.txt"),
    teardown = _ => ())

  val suspendInLambdaFixtures = FunFixture.map3(
    compilerContextWithContinuationsPlugin,
    suspendInLambdaSource,
    expectedSuspendInLambdaOutput)
}
