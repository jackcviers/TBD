package continuations

import continuations.jvm.internal.ContinuationStub
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext
import scala.collection.mutable.ArrayBuffer
import dotty.tools.dotc.config.Properties

@main def SuspendInLambdaByHand() =

  def suspendInLambda(completion: continuations.Continuation[Int])
      : Any | Null | (continuations.Continuation.State.Suspended.type) =
    val debug = Properties.envOrNone("DEBUGCONT").isDefined
    var z = 0
    (1 to 1_000_000_000).foreach { (i: Int) =>
      if(debug)
        println("hello")
      val continuation1 = completion
      val safeContinuation: continuations.SafeContinuation[Int] =
        new continuations.SafeContinuation[Int](
          continuations.intrinsics.intercepted[Int](continuation1)(),
          continuations.Continuation.State.Undecided)
      {
        {
          safeContinuation.resume(Right(i.+(1) ))
        }
      }
      val x = safeContinuation.getOrThrow()
      if(debug)
        println("world")
      z = x.asInstanceOf[Int]
    }
    z
  val begin = System.currentTimeMillis()
  val l =
    suspendInLambda(ContinuationStub.contImpl).asInstanceOf[Int]
  val end = System.currentTimeMillis()
  println(s"Elapsed: ${end - begin}")
  println(l)
