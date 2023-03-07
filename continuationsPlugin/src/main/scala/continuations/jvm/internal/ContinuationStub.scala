package continuations.jvm.internal

import continuations.Continuation
import scala.util.Properties

object ContinuationStub:
  private def c: Continuation[Any | Null] = new Continuation[Any | Null] {
    type Ctx = EmptyTuple

    def resume(value: Either[Throwable, Any | Null]): Unit =
      if(Properties.envOrNone("DEBUGCONT").isDefined)
        println("ContinuationStub.resume")

    override def context: Ctx = EmptyTuple
  }

  def contImpl: ContinuationImpl = new ContinuationImpl(c, c.context) {
    protected def invokeSuspend(
      result: Either[Throwable, Any | Null | Continuation.State.Suspended.type]): Any | Null =
      if(Properties.envOrNone("DEBUGCONT").isDefined)
        println("ContuationStub.contImpl#invokeSuspend($result)")
      result.fold(t => throw t, or => or)
  }
