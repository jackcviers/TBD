package continuations

import dotty.tools.dotc.ast.Trees.{Apply, Block, DefDef, Inlined}
import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.core.Contexts.Context
import munit.FunSuite

class TreesChecksSuite extends FunSuite, CompilerFixtures, TreesChecks {

  continuationsContextAndInlinedSuspendingTree.test(
    """|subtreeCallsSuspend(Suspend#suspendContinuation[Int] {continuation =>
       |  continuation.resume(Right(1))
       |})
       | should be true""".stripMargin) {
    case (given Context, inlinedSuspend) =>
      assert(subtreeCallsSuspend(inlinedSuspend))
  }

  continuationsContextAndOneTree.test(
    """|subtreeCallsSuspend(1) should be false""".stripMargin) {
    case (given Context, nonInlinedTree) =>
      assert(!subtreeCallsSuspend(nonInlinedTree))
  }

  continuationsContextAndInlinedSuspendingTree.test(
    """|treeCallsSuspend(Suspend#suspendContinuation[Int] {continuation =>
       |  continuation.resume(Right(1))
       |})
       | should be true""".stripMargin) {
    case (given Context, inlinedSuspend) =>
      assert(treeCallsSuspend(inlinedSuspend))
  }

  continuationsContextAndOneTree.test("""|treeCallsSuspend(1) should be false""".stripMargin) {
    case (given Context, nonInlinedTree) =>
      assert(!treeCallsSuspend(nonInlinedTree))
  }

  continuationsContextAndInlinedSuspendingTree.test(
    """|treeCallsResume(continuation.resume(Right(1)))
       | should be true""".stripMargin) {
    case (given Context, inlinedSuspend) =>
      val resume = inlinedSuspend match
        case Inlined(
              Apply(_, List(Block(_, Block(List(DefDef(_, _, _, Block(_, resumeCall))), _)))),
              _,
              _) =>
          resumeCall
        case _ => tpd.EmptyTree

      assert(treeCallsResume(resume))
  }

  continuationsContextAndInlinedSuspendingTree.test(
    """
      |treeCallsResume(Suspend#suspendContinuation[Int] {continuation =>
      |  continuation.resume(Right(1))
      |})
      | should be false
      |""".stripMargin) {
    case (given Context, inlinedSuspend) =>
      assert(!treeCallsResume(inlinedSuspend))
  }
}
