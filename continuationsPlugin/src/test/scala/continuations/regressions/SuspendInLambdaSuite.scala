package continuations
package regressions

import dotty.tools.dotc.core.Contexts.Context
import munit.FunSuite

class SuspendInLambdaSuite extends FunSuite, CompilerFixtures, SuspendInLambdaSuiteFixtures {

  suspendInLambdaFixtures.test(
    "It should transform a suspend.resume call embedded in a lambda") {
    case (given Context, source, expected) =>
      checkContinuations(source) {
        case (tree, _) =>
          assertNoDiff(cleanCompilerOutput(tree), expected)
      }

  }

}
