package continuations

import continuations.jvm.internal.ContinuationStub

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@main def SuspendInLambdaListMapConcurrentResume() =
  val ec: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
  given scheduler: ScheduledExecutorService =
    Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() - 1)

  // not the real await, but will work for our purposes today
  def await[A](c: SafeContinuation[A])(using sched: ScheduledExecutorService): A = {
    val countdownLatch = CountDownLatch(1)
    var result: Any = null
    def scheduleResultCheck: Unit = sched.schedule(
      new Runnable {
        override def run(): Unit = try {
          val res = c.getOrThrow()
          if (res != Continuation.State.Suspended && res != Continuation.State.Undecided) {
            result = res.asInstanceOf[A]
            countdownLatch.countDown()
          } else {
            Thread.`yield`()
            scheduleResultCheck
          }
        } catch {
          case _ => countdownLatch.countDown()
        }
      },
      10,
      TimeUnit.MILLISECONDS
    )
    scheduleResultCheck
    countdownLatch.await()
    result.asInstanceOf[A]
  }

  // Transformation WIP incomplete, blocking launcher incomplete,
  // using simple async/await impl
  // def suspendInLambda()(using s: Suspend): List[Int] =
  //   val z = (1 to 50).toList.map((i: Int) =>
  //     println("Hello")
  //     val x = s.await[Int](s.suspendContinuation{ c =>
  //       ec.execute(new Runnable {
  //         override def run(): Unit = {
  //           println(s"herp:$i on thread ${Thread.currentThread.getName()}")
  //           c.resume(Right(i.+(1)))
  //         }
  //       })
  //     })
  //     println("world")
  //     x
  //   )
  // becomes: 

  def suspendInLambda(completion: continuations.Continuation[List[Int]])
      : Any | Null | (continuations.Continuation.State.Suspended.type) =
    val z = (1 to 50).toList.map { (i: Int) =>
      println("Hello")
      val continuation1 = ContinuationStub.contImpl
      val safeContinuation: continuations.SafeContinuation[Int] =
        new continuations.SafeContinuation[Int](
          continuations.intrinsics.intercepted[Int](continuation1)(),
          continuations.Continuation.State.Undecided)
      ec.execute(new Runnable {
        override def run(): Unit = {
          println(s"herp:$i on thread ${Thread.currentThread.getName()}")
          safeContinuation.resume(Right(i.+(1)))
        }
      })
      val x = await[Int](safeContinuation)
      println("world")
      x
    }
    val safeContinuation: continuations.SafeContinuation[List[Int]] =
      new continuations.SafeContinuation[List[Int]](
        continuations.intrinsics.intercepted[List[Int]](completion)(),
        continuations.Continuation.State.Undecided)
    safeContinuation.resume(Right(z.asInstanceOf[List[Int]]))
    safeContinuation.getOrThrow()
  println(suspendInLambda(ContinuationStub.contImpl))
