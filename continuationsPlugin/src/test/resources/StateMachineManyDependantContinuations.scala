package continuations {
  final lazy module val compileFromString$package:
    continuations.compileFromString$package
   = new continuations.compileFromString$package()
  @SourceFile("compileFromString.scala") final module class
    compileFromString$package
  () extends Object() { this: continuations.compileFromString$package.type =>
    private def writeReplace(): AnyRef =
      new scala.runtime.ModuleSerializationProxy(classOf[continuations.compileFromString$package.type])
    class compileFromString$package$fooTest$1($completion: continuations.Continuation[Any | Null]) extends
      continuations.jvm.internal.ContinuationImpl
    ($completion, $completion.context) {
      var I$0: Any = _
      var I$1: Any = _
      var I$2: Any = _
      var I$3: Any = _
      var I$4: Any = _
      var I$5: Any = _
      var I$6: Any = _
      def I$0_=(x$0: Any): Unit = ()
      def I$1_=(x$0: Any): Unit = ()
      def I$2_=(x$0: Any): Unit = ()
      def I$3_=(x$0: Any): Unit = ()
      def I$4_=(x$0: Any): Unit = ()
      def I$5_=(x$0: Any): Unit = ()
      def I$6_=(x$0: Any): Unit = ()
      var $result: Either[Throwable, Any | Null | continuations.Continuation.State.Suspended.type] = _
      var $label: Int = _
      def $result_=(x$0: Either[Throwable, Any | Null | (continuations.Continuation.State.Suspended : continuations.Continuation.State)]): Unit = ()
      def $label_=(x$0: Int): Unit = ()
      protected override def invokeSuspend(
        result: Either[Throwable, Any | Null | (continuations.Continuation.State.Suspended : continuations.Continuation.State)]
      ): Any | Null =
        {
          this.$result = result
          this.$label = this.$label.|(scala.Int.MinValue)
          continuations.compileFromString$package.fooTest(null, this)
        }
      override def create(value: Any | Null, completion: continuations.Continuation[Any | Null]): continuations.Continuation[Unit] =
        new continuations.jvm.internal.BaseContinuationImpl(completion)
      protected def invoke(p1: Any | Null, p2: continuations.Continuation[Any | Null]): Any | Null =
        this.create(p1, p2).asInstanceOf[(BaseContinuationImpl.this : continuations.jvm.internal.BaseContinuationImpl)].invokeSuspend(
          new Right[Unit, Unit](())
        )
    }
    def fooTest(qq: Int, completion: continuations.Continuation[Int]):
      Int | Null | (continuations.Continuation.State.Suspended : continuations.Continuation.State)
     =
      {
        var qq##1: Int = qq
        var pp: Int = null
        var xx: Int = null
        var ww: Int = null
        var yy: String = null
        var tt: Int = null
        var zz: Int = null
        {
          val $continuation:
            continuations.compileFromString$package.
              compileFromString$package$fooTest$1
           =
            completion match
              {
                case
                  x$0 @
                    x$0:
                      continuations.compileFromString$package.
                        compileFromString$package$fooTest$1
                 if x$0.$label.&(scala.Int.MinValue).!=(0) =>
                  x$0.$label = x$0.$label.-(scala.Int.MinValue)
                  x$0
                case _ =>
                  new
                    continuations.compileFromString$package.
                      compileFromString$package$fooTest$1
                  (completion)
              }
          val $result: Either[Throwable, Any | Null | (continuations.Continuation.State.Suspended : continuations.Continuation.State)] =
            $continuation.$result
          $continuation.$label match
            {
              case 0 =>
                continuations.Continuation.checkResult($result)
                pp = 11
                $continuation.I$0 = qq##1
                $continuation.I$1 = pp
                $continuation.$label = 1
                val safeContinuation: continuations.SafeContinuation[Int] = continuations.SafeContinuation.init[Int]($continuation)
                {
                  safeContinuation.resume(qq##1.-(1))
                }
                safeContinuation.getOrThrow() match
                  {
                    case continuations.Continuation.State.Suspended => return continuations.Continuation.State.Suspended
                    case orThrow @ <empty> =>
                      xx = orThrow.asInstanceOf[Int]
                      return[label1] ()
                  }
              case 1 =>
                qq##1 = $continuation.I$0
                pp = $continuation.I$1
                continuations.Continuation.checkResult($result)
                xx = $result.asInstanceOf[Int]
                label1[Unit]: <empty>
                ww = 13
                val rr: String = "AAA"
                $continuation.I$0 = qq##1
                $continuation.I$1 = pp
                $continuation.I$2 = xx
                $continuation.I$3 = ww
                $continuation.$label = 2
                val safeContinuation: continuations.SafeContinuation[String] = continuations.SafeContinuation.init[String]($continuation)
                {
                  safeContinuation.resume(rr)
                }
                safeContinuation.getOrThrow() match
                  {
                    case continuations.Continuation.State.Suspended => return continuations.Continuation.State.Suspended
                    case orThrow @ <empty> =>
                      yy = orThrow.asInstanceOf[String]
                      return[label2] ()
                  }
              case 2 =>
                qq##1 = $continuation.I$0
                pp = $continuation.I$1
                xx = $continuation.I$2
                ww = $continuation.I$3
                continuations.Continuation.checkResult($result)
                yy = $result.asInstanceOf[String]
                label2[Unit]: <empty>
                tt = 100
                $continuation.I$0 = qq##1
                $continuation.I$1 = pp
                $continuation.I$2 = xx
                $continuation.I$3 = ww
                $continuation.I$4 = yy
                $continuation.I$5 = tt
                $continuation.$label = 3
                val safeContinuation: continuations.SafeContinuation[Int] = continuations.SafeContinuation.init[Int]($continuation)
                {
                  safeContinuation.resume(ww.-(1))
                }
                safeContinuation.getOrThrow() match
                  {
                    case continuations.Continuation.State.Suspended => return continuations.Continuation.State.Suspended
                    case orThrow @ <empty> => zz = orThrow.asInstanceOf[Int]
                  }
              case 3 =>
                qq##1 = $continuation.I$0
                pp = $continuation.I$1
                xx = $continuation.I$2
                ww = $continuation.I$3
                yy = $continuation.I$4
                tt = $continuation.I$5
                continuations.Continuation.checkResult($result)
                zz = $result.asInstanceOf[Int]
              case _ => throw new IllegalArgumentException("call to \'resume\' before \'invoke\' with coroutine")
            }
        }
        println(xx)
        xx.+(qq##1).+(augmentString(yy).size).+(zz).+(pp).+(tt)
      }
  }
}