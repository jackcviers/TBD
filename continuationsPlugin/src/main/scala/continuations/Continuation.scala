package continuations

import continuations.jvm.internal.BaseContinuationImpl

trait Continuation[-A]:
  type Ctx <: Tuple
  def context: Ctx
  def resume(value: Either[Throwable, A]): Unit
  def contextService[T](): T | Null =
    context.toList.find(_.isInstanceOf[T]).map(_.asInstanceOf[T]).orNull

object Continuation:
  enum State:
    case Suspended, Undecided, Resumed

  /**
   * No body needed as the compiler plugin replaces this in call sites
   */
  def suspendContinuation[A](f: Continuation[A] => Unit)(using s: Suspend): A =
    ???

abstract class RestrictedContinuation(
    completion: Continuation[Any | Null] | Null
) extends BaseContinuationImpl(completion):

  if (completion != null)
    require(completion.context == EmptyTuple)

  override type Ctx = EmptyTuple
  override val context: EmptyTuple = EmptyTuple
