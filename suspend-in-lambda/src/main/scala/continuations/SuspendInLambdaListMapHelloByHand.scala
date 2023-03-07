package continuations

import continuations.jvm.internal.ContinuationStub

@main def SuspendInLambdaListMapHelloByHand() =
  def suspendInLambda(completion: continuations.Continuation[List[Int]])
      : Any | Null | (continuations.Continuation.State.Suspended.type) =
    val z = List(1, 2, 3).map { (i: Int) =>
      println("Hello")
      val continuation1 = ContinuationStub.contImpl
      val safeContinuation: continuations.SafeContinuation[Int] =
        new continuations.SafeContinuation[Int](
          continuations.intrinsics.intercepted[Int](continuation1)(),
          continuations.Continuation.State.Undecided)
      {
        {
          safeContinuation.resume(Right(i.+(1)))
        }
      }
      safeContinuation.getOrThrow()
      println("world")
    }
    val safeContinuation: continuations.SafeContinuation[List[Int]] =
      new continuations.SafeContinuation[List[Int]](
        continuations.intrinsics.intercepted[List[Int]](completion)(),
        continuations.Continuation.State.Undecided)
    safeContinuation.resume(Right(z.asInstanceOf[List[Int]]))
    safeContinuation.getOrThrow()
  println(suspendInLambda(ContinuationStub.contImpl))
