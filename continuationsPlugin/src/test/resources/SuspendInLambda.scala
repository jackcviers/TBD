package continuations

def f()(using s: Suspend): List[Int] =
  List(1, 2, 3).map(i => s.suspendContinuation(_.resume(Right(i + 1))))
