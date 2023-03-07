package continuations

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import java.util.concurrent.TimeUnit
import scala.util.Properties
import continuations.jvm.internal.ContinuationStub
import cats.effect.IO
import cats.effect.unsafe.implicits.global

class ContinuationsBenchmark {

  private val debug = Properties.envOrNone("DEBUGCONT").isDefined

  private def catsEffectListMap(): Int =
    var z = 0
    List(1).foreach { (i: Int) => z = IO(i + 1).unsafeRunSync() }
    z

  private def suspendInLambda(completion: continuations.Continuation[Int])
      : Any | Null | (continuations.Continuation.State.Suspended.type) =

    var z = 0
    List(1).foreach { (i: Int) =>
      val continuation1 = completion
      val safeContinuation: continuations.SafeContinuation[Int] =
        new continuations.SafeContinuation[Int](
          continuations.intrinsics.intercepted[Int](continuation1)(),
          continuations.Continuation.State.Undecided)
      {
        {
          safeContinuation.resume(Right(i.+(1)))
        }
      }
      val x = safeContinuation.getOrThrow()
      z = x.asInstanceOf[Int]
    }
    z

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def measureThroughput = suspendInLambda(ContinuationStub.contImpl).asInstanceOf[Int]

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def measureCatsThroughput = catsEffectListMap()

  private def rawScala() =
    var z = 0
    List(1).foreach { (i: Int) =>
      val x = i.+(1)
      z = x.asInstanceOf[Int]
    }
    z

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def measureRawThroughput = rawScala()

}
