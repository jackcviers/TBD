package continuations

import jdk.incubator.concurrent.StructuredTaskScope
import org.openjdk.jmh.annotations.{State => S}
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.{Scope => SC}
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import java.util.concurrent.TimeUnit
import scala.util.Properties
import continuations.jvm.internal.ContinuationStub
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import java.util.concurrent.Callable
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext

// import zio._

// import kyo.core._
// import kyo.ios._
// import kyo.KyoApp
// import ox.*
// import ox.given

// def ThreeDependentContinuationsLoop =
//   def threeDependentContinuations(a: Int, b: Int, c: Int)(using s: Suspend): Int =
//     val d = 4
//     val continuationOne: Int = s.suspendContinuation(_.resume(Right(d + a))) // 5
//     val e = 5
//     val continuationTwo: Int =
//       s.suspendContinuation(_.resume(Right(continuationOne + e + b))) // 12
//     val f = 6
//     val result: Int = s.suspendContinuation(_.resume(Right(continuationTwo + f + c))) // 21
//     result
//   def threeDependentContinuationsLoop()(using Suspend): Int =
//     var i = 100
//     var z = 0
//     while (i > 0) {
//       z = threeDependentContinuations(1, 2, 3)
//       i -= 1
//     }
//     z
//   threeDependentContinuationsLoop()

// def CatsContinuationsLoop =
//   def dependentFlatMaps(a: Int, b: Int, c: Int): Int =
//     IO.pure(4)
//       .map(d => d + a)
//       .flatMap(continuationOne => IO.pure(5).map(e => continuationOne + e + b))
//       .flatMap { continuationTwo => IO.pure(6).map(f => continuationTwo + f + c) }
//       .unsafeRunSync()
//   dependentFlatMaps(1,2,3)

def Passthrough =
  def passthrough(a: Int, b: Int, c: Int)(using Suspend): Int =
    a + b + c
  passthrough(1, 2, 3)

def CatsIO =
  def catsIO(a: Int, b: Int, c: Int): Int =
    IO(a + b + c).unsafeRunSync()
  catsIO(1,2,3)

object LoomConstants{
  val sts = new StructuredTaskScope[Int]()
}

def Loom =
  def loom(a: Int, b: Int, c: Int): Int =
    LoomConstants
      .sts
      .fork(
        new Callable[Int]{
          override def call(): Int = a + b + c
        }
      ).get
  loom(1,2,3)

def ScalaFuture() =
  def future(a: Int, b: Int, c:Int): Int =
    Await.result(Future{a + b + c}(ExecutionContext.Implicits.global), Duration.Inf)
  future(1,2,3)

def SuspendedScalaCode =
  def suspendedScalaCode(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    s.shift(_.resume(a + b + c))
  suspendedScalaCode(1,2,3)

def PassthroughAfterFirstSuspension =
  def passthroughAfterFirstSuspension(a: Int, b: Int, c: Int)(using s:Suspend): Int =
    val x = s.shift[Int](_.resume(a + b))
    x + c
  passthroughAfterFirstSuspension(1,2,3)


def TwoSuspensionsAddition =
  def twoSuspensionsAddition(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    val x = s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(x + c))
  twoSuspensionsAddition(1,2,3)


def TwoSuspensionsAdditionCodeAfter =
  def twoSuspensionsAddition(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    val x = s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(x + c))
  twoSuspensionsAddition(1,2,3)

def OneSuspensionPoint =
  def suspensionPoints(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    s.shift[Int](_.resume(a + b))
  suspensionPoints(1,2,3)

def TwoSuspensionPoint =
  def suspensionPoints(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
  suspensionPoints(1,2,3)

def ThreeSuspensionPoint =
  def suspensionPoints(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
  suspensionPoints(1,2,3)

def FourSuspensionPoint =
  def suspensionPoints(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
  suspensionPoints(1,2,3)

def FiveSuspensionPoint =
  def suspensionPoints(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
  suspensionPoints(1,2,3)

def SixSuspensionPoint =
  def suspensionPoints(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
  suspensionPoints(1,2,3)

def SevenSuspensionPoint =
  def suspensionPoints(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
  suspensionPoints(1,2,3)

def EightSuspensionPoint =
  def suspensionPoints(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
  suspensionPoints(1,2,3)

def NineSuspensionPoint =
  def suspensionPoints(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
  suspensionPoints(1,2,3)

def TenSuspensionPoints = 
  def suspensionPoints(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
  suspensionPoints(1,2,3)

def ElevenSuspensionPoints = 
  def suspensionPoints(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
  suspensionPoints(1,2,3)

def TwelveSuspensionPoints = 
  def suspensionPoints(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
  suspensionPoints(1,2,3)

def ThirteenSuspensionPoints = 
  def suspensionPoints(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
  suspensionPoints(1,2,3)

def FourteenSuspensionPoints = 
  def suspensionPoints(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
  suspensionPoints(1,2,3)

def FifteenSuspensionPoints = 
  def suspensionPoints(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
  suspensionPoints(1,2,3)

def SixteenSuspensionPoints = 
  def suspensionPoints(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
  suspensionPoints(1,2,3)

def SeventeenSuspensionPoints = 
  def suspensionPoints(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
  suspensionPoints(1,2,3)

def EightteenSuspensionPoints = 
  def suspensionPoints(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
  suspensionPoints(1,2,3)

def NineteenSuspensionPoints = 
  def suspensionPoints(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
  suspensionPoints(1,2,3)

def OneHundredSuspensionPoints =
  def suspensionPoints(a: Int, b: Int, c: Int)(using s: Suspend): Int =
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
    s.shift[Int](_.resume(a + b))
  suspensionPoints(1,2,3)

// def OneContinuation =
//   def oneContinuation(a: Int, b: Int, c: Int)(using s: Suspend): Int =
//     s.suspendContinuation(_.resume(Right(a + b + c)))
//   oneContinuation(1,2,3)

// def TwoContinuations =
//   def twoContinuations(a: Int, b: Int, c: Int)(using s: Suspend): Int =
//     val x = s.shift[Int](_.resume(a + 1))
//     s.shift[Int](_.resume(x + b + c))

//   def cont2()(using s: Suspend): Unit = s.shift { c =>
//     c.resume(Right[Nothing, Unit](println("hello")))
//   }
//   def cont1()(using s: Suspend): Unit = cont2()

//   def contCallsContInSuspend()(using s: Suspend): Unit = s.shift{c => c.resume(Right(cont2()))}

//   twoContinuations(1,2,3)
//   // cont1()
//   // contCallsContInSuspend()

// def ThreeContinuations =
//   def threeContinuations(a: Int, b: Int, c: Int)(using s: Suspend): Int =
//     val x = s.suspendContinuation[Int](_.resume(Right(a + 1)))
//     val y = s.suspendContinuation[Int](_.resume(Right(x + b)))
//     s.suspendContinuation[Int](_.resume(Right(y + c)))
//   threeContinuations(1,2,3)

// def CallTwoSuspends =
//   def twoContinuation(a: Int, b: Int, c: Int)(using s: Suspend): Int =
//     s.suspendContinuation(_.resume(Right(a + b + c)))
//   def oneContinuation(a: Int, b: Int, c: Int)(using s: Suspend): Int =
//     val x = twoContinuation(a, b, c)
//     s.suspendContinuation(_.resume(Right(x + a + b + c)))
//   oneContinuation(1,2,3)

// // def MeasureNSuspends(n: Int) =
// //   var nn = n
// //   def nSuspends(a: Int, b: Int, c: Int)(using s: Suspend): Unit =
// //     s.suspendContinuation[Int](con => con.resume(Right(a + b + c)))
// //   while(nn > 0){
// //     nSuspends(1,2,3)
// //     nn -= 1
// //   }

// def ThreeDependentContinuations =
//   def threeDependentContinuations(a: Int, b: Int, c: Int)(using s: Suspend): Int =
//     // val d = 4
//     // val continuationOne: Int = s.suspendContinuation(_.resume(Right(d + a))) // 5
//     // val e = 5
//     // val continuationTwo: Int =
//     //   s.suspendContinuation(_.resume(Right(continuationOne + e + b))) // 12
//     // val f = 6
//     val result: Int = s.suspendContinuation(_.resume(Right(a + b + c))) // 21
//     result
//   threeDependentContinuations(1, 2, 3)

object ContState {
  // val runtime = zio.Runtime.default

}

class ContinuationsBenchmark {

  // private def oxListMap(): Int =
  //   var z = 0
  //   List(1).foreach{(i: Int) =>
  //     val x = Ox.uninterruptible(i + 1)
  //     z = x
  //   }
  //   z

  // private def kyoListMap(): Int =
  //   var z = 0
  //   List(1).foreach{(i: Int) =>
  //     val x = KyoApp.run(scala.concurrent.duration.Duration.Inf)(i+1)
  //     z = x
  //   }
  //   z

  // private def scalaFutureListMap(): Int =
  //   var z = 0
  //   List(1).foreach{(i: Int) =>
  //     val f =
  //       scala.concurrent.Await.result(scala.concurrent.Future(i+1)(scala.concurrent.ExecutionContext.global), scala.concurrent.duration.Duration.Inf)
  //     z = f
  //   }
  //   z

  // private def zioListMap(): Int =
  //   var z = 0
  //   Unsafe.unsafe { implicit unsafe =>
  //     List(1).foreach{(i: Int) =>
  //       z = ContState.runtime.unsafe.run(zio.ZIO.attempt(i+1)).getOrThrowFiberFailure()
  //     }
  //   }
  //   z

  // private def catsEffectListMap(): Int =
  //   var z = 0
  //   List(1).foreach { (i: Int) => z = IO(i + 1).unsafeRunSync() }
  //   z

  def MeasureRawScala =
    def rawScala(a:Int, b: Int, c: Int): Int = a + b + c
    rawScala(1,2,3)

  def MeasureRawTwoStepsScala =
    def rawScala(a:Int, b: Int, c: Int): Int =
      val x = a + b
      x + c
    rawScala(1,2,3)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def measureTenSuspends = TenSuspensionPoints

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def measureRawScala = MeasureRawScala

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def measurePassthrough = Passthrough

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def measureOneHundredSuspends = OneHundredSuspensionPoints

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def measureCatsIO = CatsIO

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def measureLoom = Loom

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def measureFuture = ScalaFuture()


}
