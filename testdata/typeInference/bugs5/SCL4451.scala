def f(seq: Seq[Int]) = for (y <- seq) yield y
/*start*/f(Array(1, 2, 3)).getClass/*end*/
//Class[_ <: Seq[Int]]