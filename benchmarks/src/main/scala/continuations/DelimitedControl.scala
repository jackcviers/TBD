package continuations

import scala.annotation.targetName
import scala.annotation.unchecked.uncheckedVariance

inline def safeCast[A](inline a: Any, explanation: String): A = a.asInstanceOf[A]

/**
 * User facing API
 *
 * This is very close to the strawman:
 *   Strawman: Suspensions for algebraic effects #16739
 *   <https://github.com/lampepfl/dotty/pull/16739>
 */
trait API {

  /**
   * A (delimited) continuation can be thought of as a function
   * from [[A]] (the expected type at the callsite of [[suspend]]
   * to [[R]] (the type at the delimiter [[boundary]]).
   *
   * Calling [[resume]] on the continuation restores the computational
   * context to the point at which [[suspend]] has been called, originally.
   * Computation then proceeds with value [[A]] as a result of [[suspend]].
   *
   * Calling [[cancel]] also restores the computational context. However,
   * then a cancellation exception is thrown to finalize resources.
   */
  type Continuation[-A, +R]
  extension [A, B](k: Continuation[A, B])
    def resume(value: A): B
    def cancel(exception: Exception): Nothing = ??? // TODO implement

  /**
   * A capability that allows suspending (i.e., [[suspend]]) or aborting (i.e., [[raise]]) computation.
   */
  type CanSuspend[R]

  extension [R](capability: CanSuspend[R])
    def suspend[A](body: Continuation[A, R] => R): A
    def raise(value: R): Nothing = ??? // TODO implement

  /**
   * Running `boundary { c => ... }` introduces a fresh capability of type [[CanSuspend]]
   * and marks the extend to which a continuation can be captured by [[suspend]].
   */
  def boundary[R](prog: CanSuspend[R] => R): R
}


/**
 * API used by the instrumentation.
 *
 * The various methods of [[Runtime]] are marked as `inline`
 * so that it comes close to an instrumentation performed
 * by the compiler (in terms of additional frames on the native stack, etc.)
 */
trait Runtime {

  /**
   * Representation of a single frame
   *
   * Frames can be thought of as functions from [[A]] to [[B]]; they can be applied
   * with [[apply]] and are constructed as part of the instrumentation of an [[entrypoint]].
   */
  type Frame[A, B]

  /**
   * Resume computation of a single frame
   */
  extension [A, B](f: Frame[A, B]) def apply(value: A): B

  /**
   * Mark an entrypoint--equip it with the capability to save the current state of the active frame
   */
  inline def entrypoint[A, B](inline prog: => A)(inline state: => Frame[A, B]): A

  /**
   * Run an instrumented program; should set up the runtime and prepare the program for execution.
   */
  inline def run[R](inline prog: => R): R
}

/**
 * Unique runtime prompts that can be compared for equality.
 */
trait Prompts {
  opaque type Prompt = Int
  private var _last = 0;
  def freshPrompt(): Prompt = { _last += 1; _last }
}

/**
 * Capturing the continuation involves handling various kinds of segments
 * of the runtime callstack (see README).
 */
trait Stacks extends Prompts, Runtime {

  /**
   * The initial part of the continuation which is being captured at the moment
   *
   * It does not contain prompts and is typically a list of frames in reversed order (not a "proper" stack)
   */
  type Frames[A, R]

  /**
   * A delimited stack segment (part of the meta continuation and mounted as part of the trampoline)
   */
  type Stack[A, R]

  /**
   * A "stack of stacks" (contains prompts, in reverse order, corresponds to the **captured** continuation)
   */
  type Resumption[A, R]


  // Constructing Stacks
  // -------------------

  def emptyFrames[A]: Frames[A, A]
  def emptyResumption[A]: Resumption[A, A]
  def emptyStack[A]: Stack[A, A]

  // append [f1, f2, f3] f4 = [f1, f2, f3, f4]
  //         ^^
  //  most "recent" frame
  def pushFrame[A, B, C](rest: Frames[A, B], frame: Frame[B, C]): Frames[A, C]

  def reverseOnto[A, B, C](init: Frames[A, B], tail: Stack[B, C]): Stack[A, C]

  def pushStack[A, B, C](cont: Resumption[A, B], prompt: Prompt, pure: Stack[B, C]): Resumption[A, C]


  // Destructing Stacks
  // ------------------

  extension [A, B] (self: Resumption[A, B])
    @targetName("nonEmptyResumption")
    def nonEmpty: Boolean
    def popStack[X]: (Resumption[A, X], Prompt, Stack[X, B])


  extension[A, B] (self: Stack[A, B])
    @targetName("nonEmptyStack")
    def nonEmpty: Boolean
    def popFrame[X]: (Frame[A, X], Stack[X, B])
}

/**
 * Facilities to implement tailcalls on platforms that do not
 * provide them natively.
 *
 * - Each non-tailcall needs to be wrapped into [[forceTailcall]]
 * - Each to-be-optimized tailcall needs to be wrappend into [[tailCall]]
 */
trait TailCalls {
  case class TailCall[T](thunk: () => T) extends Throwable(null, null, false, false)

  inline def tailCall[R](inline call: => R): R = throw TailCall(() => call)

  inline def forceTailcall[R](inline prog: => R): R = {
    var thunk: () => R = () => prog;
    var result: Option[R] = None
    while (result.isEmpty) try { result = Some(thunk()) } catch {
      case r: TailCall[R] => thunk = r.thunk
    }
    result.get
  }
}

trait StateMachineRuntime extends Runtime {

  type PC = Int

  // a very inefficient draft implementation
  type Locals = Map[String, Any]

  case class Frame[-A, +B](implementation: FrameData => B, pc: PC, locals: Locals)
  extension [A, B](self: Frame[A, B])
    def apply(value: A) = self.implementation(FrameData(self.pc, self.locals, value))

  case class FrameData(pc: PC, locals: Locals, res: Any) {
    def result[T](): T = safeCast[T](res, "Needs to be asserted by the instrumentation itself.")
    def local[A](key: String): A = safeCast[A](locals(key), "Needs to be asserted by the instrumentation itself.")
  }
}

trait ContinuationRuntime extends Runtime {

  type Frame[-A, +B] = A => B

  extension [A, B](self: Frame[A, B])
    def apply(value: A) = self(value)
}



trait GeneralizedExceptions extends API, Runtime, Stacks, Prompts, TailCalls {

  // Public API
  // ----------
  type Continuation[-A, +R] = Resumption[A, R] @uncheckedVariance
  extension [A, B](k: Continuation[A, B])
    def resume(value: A): B = tailCall { rewind(k, value) }

  type CanSuspend[T] = Prompt
  extension [R](capability: CanSuspend[R])
    def suspend[A](body: Continuation[A, R] => R): A =
      throw Suspend(body, capability, emptyFrames, emptyResumption)

  def boundary[R](prog: CanSuspend[R] => R): R =
    val p = freshPrompt()
    trampoline(p, emptyStack) { prog(p) }

  // Implementation Details
  // ----------------------
  case class Suspend[A, X, Y, R](
    body: Continuation[A, R] => R,
    prompt: Prompt,
    pure: Frames[X, Y],
    cont: Resumption[A, X]
  ) extends Throwable(null, null, false, false)

  inline def entrypoint[S, T](inline prog: => S)(inline state: => Frame[S, T]): S =

    try { forceTailcall { prog } } catch {
      case s: Suspend[a, x, S, r] =>
        throw Suspend[a, x, T, r](s.body, s.prompt, pushFrame(s.pure, state), s.cont)
    }

  inline def run[R](inline prog: => R): R = forceTailcall { prog }

  private def rewind[A, B](k: Resumption[A, B], value: A): B =
    if k.nonEmpty then k.popStack[Any] match {
      case (k, prompt, stack) => trampoline(prompt, stack) { rewind(k, value) }
    }
    else safeCast[B](value, "we know that the continuation goes from A to A, thus value has the right type.")


  // installs the trampoline (mounts the stack)
  private inline def trampoline[A, R](prompt: Prompt, stack: Stack[A, R])(inline prog: => A): R = {
    var rest = stack

    try {
      var curr: A = forceTailcall[A] { prog }

      while (rest.nonEmpty) rest.popFrame match {
        /* the type arguments are a lie, here: we pretend to always be working on type A. */
        case (f: Frame[A, A] @unchecked, tail: Stack[A, R] @unchecked) =>
          rest = tail
          curr = forceTailcall { safeCast[A](f.apply(curr), "Since type arguments above are a lie.") }
      }

      safeCast[R](curr, "in the end the stack is empty, and we know A = R")
    } catch {
      case s: Suspend[a, x, A, R] =>
        //  (pure.reverse ::: rest, p) :: cont
        val k = pushStack(s.cont, prompt, reverseOnto(s.pure, rest))
        // right prompt (r == R)
        if (s.prompt == prompt) {
          tailCall { s.body(k) }
        // wrong prompt
        } else {
          throw Suspend(s.body, s.prompt, emptyFrames, k)
        }
    }
  }
}

trait ListStacks extends Stacks {
  type Frames[A, R] = List[Frame[Any, Any]]
  type Stack[A, R] = List[Frame[Any, Any]]

  sealed trait Resumption[A, R]
  case class Empty[A]() extends Resumption[A, A]
  case class Segment[A, B, C](head: Stack[B, C], prompt: Prompt, tail: Resumption[A, B]) extends Resumption[A, C]

  def emptyFrames[A]: Frames[A, A] = List.empty
  def emptyResumption[A]: Resumption[A, A] = Empty()
  def emptyStack[A]: Stack[A, A] = List.empty

  def pushFrame[A, B, C](rest: Frames[A, B], frame: Frame[B, C]): Frames[A, C] =
    safeCast[Frame[Any, Any]](frame, "Purposefully loosing type information here.") :: rest

  @scala.annotation.tailrec
  final def reverseOnto[A, B, C](init: Frames[A, B], tail: Stack[B, C]): Stack[A, C] = init match {
    case first :: rest => reverseOnto(rest, first :: tail)
    case Nil => tail
  }

  def pushStack[A, B, C](cont: Resumption[A, B], prompt: Prompt, pure: Stack[B, C]): Resumption[A, C] =
    Segment(pure, prompt, cont)

  extension [A, B] (self: Stack[A, B])
    @targetName("nonEmptyStack")
    def nonEmpty = self.nonEmpty
    def popFrame[X] = (safeCast[Frame[A, X]](self.head, "Recovering information from the type aligned sequence"), self.tail)

  extension [A, B] (self: Resumption[A, B])
    @targetName("nonEmptyResumption")
    def nonEmpty = self match {
      case Empty() => false
      case _ => true
    }
    def popStack[X]: (Resumption[A, X], Prompt, Stack[X, B]) = self match {
      case Segment(head, prompt, tail) => (safeCast[Resumption[A, X]](tail, "Recovering types from the type-aligned sequence."), prompt, head)
      case _ => ???
    }
}

/**
 * An example of the traditional "state machine" encoding with a "twist": `foo` and `bar` are the
 * original methods in direct style. We will always try to call the direct style, whenever possible.
 *
 * Advantage of the state machine encoding:
 * - all entry points can be modeled with a single instrumented method.
 *
 * Disadvantage of the state machine encoding:
 * - a lot of indirect jumps (through the while loop).
 * - transformation itself is quite elaborate.
 */
object ContinuationExamples extends API, StateMachineRuntime, GeneralizedExceptions, ListStacks {

  def bar(s: CanSuspend[Int]): Int = s.suspend[Int] { k => // println("Resuming with 1");
    k.resume(1) }

  def foo(s: CanSuspend[Int]): Int = {
    var x = 10;
    while (x > 0) {
      val res = entrypoint { bar(s) } { Frame(foo$, 1, Map("x" -> x, "s" -> s)) }
      x = x - res;
    }
    return x
  }

  // Traditional Statemachine Version
  // --------------------------------
  // observation we made: having specialized frames with fields has better performance
  //   than generically loading variables
  def foo$(frame: FrameData): Int = {
    var pc = frame.pc;
    // println(s"Entering foo at ${frame.pc}")

    // locals
    var s: CanSuspend[Int] = freshPrompt() // TODO
    var res: Int = 0
    var x: Int = 0

    while (true) pc match {
      // loop
      case 0 =>
        if (x > 0) {
          // same Control code as above.
          res = entrypoint { bar(s) } { Frame(foo$, 1, Map("x" -> x, "s" -> s)) }
          pc = 2 // jump
        } else {
          return x
        }

      // entry point 1
      case 1 =>
        x = frame.local("x")
        s = frame.local("s")
        res = frame.result[Int]()
        pc = 2 // jump

      // continuation of calling bar
      case 2 =>
        x = x - res;
        pc = 0; // jump to loop
    }

    // unreachable:
    return 0
  }


  def bar2(s: CanSuspend[Int]): Int = 1

  def foo2(s: CanSuspend[Int]): Int = {
    var x = 10;
    while (x > 0) {
      val res = entrypoint { bar2(s) } { Frame(foo2$, 1, Map("x" -> x, "s" -> s)) }
      x = x - res;
    }
    return x
  }

  // Traditional Statemachine Version
  // --------------------------------
  // observation we made: having specialized frames with fields has better performance
  //   than generically loading variables
  def foo2$(frame: FrameData): Int = {
    var pc = frame.pc;
    // println(s"Entering foo at ${frame.pc}")

    // locals
    var s: CanSuspend[Int] = freshPrompt() // TODO
    var res: Int = 0
    var x: Int = 0

    while (true) pc match {
      // loop
      case 0 =>
        if (x > 0) {
          // same Control code as above.
          res = entrypoint { bar(s) } { Frame(foo2$, 1, Map("x" -> x, "s" -> s)) }
          pc = 2 // jump
        } else {
          return x
        }

      // entry point 1
      case 1 =>
        x = frame.local("x")
        s = frame.local("s")
        res = frame.result[Int]()
        pc = 2 // jump

      // continuation of calling bar
      case 2 =>
        x = x - res;
        pc = 0; // jump to loop
    }

    // unreachable:
    return 0
  }


  def runLoop() = run {
    boundary[Int] { s =>
      foo(s)
    }
  }

  def runLoop2() = run {
    boundary[Int] { s =>
      foo2(s)
    }
  }
}
