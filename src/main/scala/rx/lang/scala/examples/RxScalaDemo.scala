/**
 * Copyright 2013 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rx.lang.scala.examples

import org.scalatest.junit.JUnitSuite
import scala.language.postfixOps
import rx.lang.scala._
import scala.concurrent.duration._
import org.junit.{Before, Test, Ignore}
import org.junit.Assert._
import rx.lang.scala.concurrency.NewThreadScheduler

//@Ignore // Since this doesn't do automatic testing, don't increase build time unnecessarily
class RxScalaDemo extends JUnitSuite {

  @Test def intervalExample() {
    val o = Observable.interval(200 millis).take(5)
    o.subscribe(n => println("n = " + n))
    
    // need to wait here because otherwise JUnit kills the thread created by interval()
    waitFor(o)
    
    println("done")
  }
  
  def msTicks(start: Long, step: Long): Observable[Long] = {
    // will be easier once we have Observable.generate method
    Observable.interval(step millis) map (_ * step + start)
  }
  
  def prefixedTicks(start: Long, step: Long, prefix: String): Observable[String] = {
    msTicks(start, step).map(prefix + _)
  }
  
  @Test def testTicks() {
    val o = prefixedTicks(5000, 500, "t = ").take(5)
    o.subscribe(output(_))
    waitFor(o)
  }
  
  @Test def testSwitch() {
    // We do not have ultimate precision: Sometimes, 747 gets through, sometimes not
    val o = Observable.interval(1000 millis).map(n => prefixedTicks(0, 249, s"Observable#$n: "))
                 .switch.take(16)
    o.subscribe(output(_))
    waitFor(o)
  }
  
  @Test def testSwitchOnObservableOfInt() {
    // Correctly rejected with error 
    // "Cannot prove that Observable[Int] <:< Observable[Observable[U]]"
    // val o = Observable(1, 2).switch
  }

  @Test def testObservableComparison() {
    val first = Observable(10, 11, 12)
    val second = Observable(10, 11, 12)
    
    val b1 = (first zip second) map (p => p._1 == p._2) forall (b => b)
    
    val equality = (a: Any, b: Any) => a == b
    val b2 = (first zip second) map (p => equality(p._1, p._2)) forall (b => b)
    
    assertTrue(b1.toBlockingObservable.single)
    assertTrue(b2.toBlockingObservable.single)
  }
  
  @Test def testObservableComparisonWithForComprehension() {
    val first = Observable(10, 11, 12)
    val second = Observable(10, 11, 12)
    
    val booleans = for ((n1, n2) <- (first zip second)) yield (n1 == n2)
    
    val b1 = booleans.forall(_ == true) // without `== true`, b1 is assigned the forall function
    
    assertTrue(b1.toBlockingObservable.single)
  }
  
  @Test def testStartWithIsUnnecessary() {
    val before = Observable(-2, -1, 0)
    val source = Observable(1, 2, 3)
    println((before ++ source).toBlockingObservable.toList)
  }

  @Test def mergeTwoExample() {
    val slowNumbers = Observable.interval(400 millis).take(5).map("slow " + _)
    val fastNumbers = Observable.interval(200 millis).take(10).map("fast " + _)
    val o = (slowNumbers merge fastNumbers)
    o.subscribe(output(_))
    waitFor(o)
  }
  
  def myInterval(period: Long): Observable[String] = {
    Observable.interval(period.millis).map(n => s"Obs-$period emits $n")
  }
  
  @Test def mergeManyExample() {
    val o = Observable.interval(500 millis).map(n => myInterval((n+1)*100))
    val stopper = Observable.interval(5 seconds)
    o.merge.takeUntil(stopper).toBlockingObservable.foreach(println(_))
  }
  
  @Test def mergeSomeExample() {
    // To merge some observables which are all known already:
    Observable(
        Observable.interval(200 millis), 
        Observable.interval(400 millis), 
        Observable.interval(800 millis)
    ).merge.take(12).toBlockingObservable.foreach(println(_))
  }    
  
  @Test def rangeAndBufferExample() {
    val o = Observable(1 to 18)
    o.buffer(5).subscribe((l: Seq[Int]) => println(l.mkString("[", ", ", "]")))
  }
  
  @Test def windowExample() {
    // this will be nicer once we have zipWithIndex
    (for ((o, i) <- Observable(1 to 18).window(5) zip Observable(0 until 4); n <- o) 
      yield s"Observable#$i emits $n")
        .subscribe(output(_))
  }
  
  @Test def testReduce() {
    assertEquals(10, Observable(1, 2, 3, 4).reduce(_ + _).toBlockingObservable.single)
  }
  
  @Test def testForeach() {
    val numbers = Observable.interval(200 millis).take(3)
    
    // foreach is not available on normal Observables:
    // for (n <- numbers) println(n+10)
    
    // but on BlockingObservable, it is:
    for (n <- numbers.toBlockingObservable) println(n+10)
  }
  
  @Test def testForComprehension() {
    val observables = Observable(Observable(1, 2, 3), Observable(10, 20, 30))
    val squares = (for (o <- observables; i <- o if i % 2 == 0) yield i*i)
    assertEquals(squares.toBlockingObservable.toList, List(4, 100, 400, 900))
  }
  
  @Test def testTwoSubscriptionsToOneInterval() {
    val o = Observable.interval(100 millis).take(8)
    o.subscribe(
        i => println(s"${i}a (on thread #${Thread.currentThread().getId()})")
    )
    o.subscribe(
        i => println(s"${i}b (on thread #${Thread.currentThread().getId()})")
    )
    waitFor(o)
  }
  
  @Test def schedulersExample() {
    val o = Observable.interval(100 millis).take(8)
    o.observeOn(NewThreadScheduler).subscribe(
        i => println(s"${i}a (on thread #${Thread.currentThread().getId()})")
    )
    o.observeOn(NewThreadScheduler).subscribe(
        i => println(s"${i}b (on thread #${Thread.currentThread().getId()})")
    )
    waitFor(o)
  }
  
  @Test def testGroupByThenFlatMap() {
    val m = Observable(1, 2, 3, 4)
    val g = m.groupBy(i => i % 2)
    val t = g.flatMap((p: (Int, Observable[Int])) => p._2)
    assertEquals(List(1, 2, 3, 4), t.toBlockingObservable.toList)    
  }
  
  @Test def testGroupByThenFlatMapByForComprehension() {
    val m = Observable(1, 2, 3, 4)
    val g = m.groupBy(i => i % 2)
    val t = for ((i, o) <- g; n <- o) yield n
    assertEquals(List(1, 2, 3, 4), t.toBlockingObservable.toList)    
  }
  
  @Test def testGroupByThenFlatMapByForComprehensionWithTiming() {
    val m = Observable.interval(100 millis).take(4)
    val g = m.groupBy(i => i % 2)
    val t = for ((i, o) <- g; n <- o) yield n
    assertEquals(List(0, 1, 2, 3), t.toBlockingObservable.toList)    
  }

  @Test def timingTest() {
    val firstOnly = false
    val numbersByModulo3 = Observable.interval(1000 millis).take(9).groupBy(_ % 3)
    
    (for ((modulo, numbers) <- numbersByModulo3) yield {
      println("Observable for modulo" + modulo + " started")
      
      if (firstOnly) numbers.take(1) else numbers
    }).merge.toBlockingObservable.foreach(println(_)) 
  }
  
  @Test def timingTest1() {
    val numbersByModulo3 = Observable.interval(1000 millis).take(9).groupBy(_ % 3)
    
    val t0 = System.currentTimeMillis
    
    (for ((modulo, numbers) <- numbersByModulo3) yield {
      println("Observable for modulo" + modulo + " started at t = " + (System.currentTimeMillis - t0))
      numbers.take(1) // <- TODO very unexpected
      //numbers
    }).merge.toBlockingObservable.foreach(println(_))
  }
  
  @Test def groupByExample() {
    val medalsByCountry = Olympics.mountainBikeMedals.groupBy(medal => medal.country)
    
    val firstMedalOfEachCountry = 
      for ((country, medals) <- medalsByCountry; firstMedal <- medals.take(1)) yield firstMedal
      
    firstMedalOfEachCountry.subscribe(medal => {
      println(s"${medal.country} wins its first medal in ${medal.year}")
    })
    
    waitFor(firstMedalOfEachCountry)
  }
  
  @Test def olympicsExample() {
    val (go, medals) = Olympics.mountainBikeMedals.publish
    medals.subscribe(println(_))
    go()
    waitFor(medals)    
  }
  
  @Test def exampleWithoutPublish() {
    val unshared = Observable(1 to 4)
    unshared.subscribe(n => println(s"subscriber 1 gets $n"))
    unshared.subscribe(n => println(s"subscriber 2 gets $n"))
  }
  
  @Test def exampleWithPublish() {
    val unshared = Observable(1 to 4)
    val (startFunc, shared) = unshared.publish
    shared.subscribe(n => println(s"subscriber 1 gets $n"))
    shared.subscribe(n => println(s"subscriber 2 gets $n"))
    startFunc()
  }
  
  def doLater(waitTime: Duration, action: () => Unit): Unit = {
    Observable.interval(waitTime).take(1).subscribe(_ => action())
  }
  
  @Test def exampleWithoutReplay() {
    val numbers = Observable.interval(1000 millis).take(6)
    val (startFunc, sharedNumbers) = numbers.publish
    sharedNumbers.subscribe(n => println(s"subscriber 1 gets $n"))
    startFunc()
    // subscriber 2 misses 0, 1, 2!
    doLater(3500 millis, () => { sharedNumbers.subscribe(n => println(s"subscriber 2 gets $n")) })
    waitFor(sharedNumbers)
  }
  
  @Test def exampleWithReplay() {
    val numbers = Observable.interval(1000 millis).take(6)
    val (startFunc, sharedNumbers) = numbers.replay
    sharedNumbers.subscribe(n => println(s"subscriber 1 gets $n"))
    startFunc()
    // subscriber 2 subscribes later but still gets all numbers
    doLater(3500 millis, () => { sharedNumbers.subscribe(n => println(s"subscriber 2 gets $n")) })
    waitFor(sharedNumbers)
  }
  
  @Test def testSingleOption() {
    assertEquals(None,    Observable(1, 2).toBlockingObservable.singleOption)
    assertEquals(Some(1), Observable(1)   .toBlockingObservable.singleOption)
    assertEquals(None,    Observable()    .toBlockingObservable.singleOption)
  }
  
  // We can't put a general average method into Observable.scala, because Scala's Numeric
  // does not have scalar multiplication (we would need to calculate (1.0/numberOfElements)*sum)
  def doubleAverage(o: Observable[Double]): Observable[Double] = {
    for ((finalSum, finalCount) <- o.fold((0.0, 0))({case ((sum, count), elem) => (sum+elem, count+1)}))
      yield finalSum / finalCount
  }
  
  @Test def averageExample() {
    println(doubleAverage(Observable()).toBlockingObservable.single)
    println(doubleAverage(Observable(0)).toBlockingObservable.single)
    println(doubleAverage(Observable(4.44)).toBlockingObservable.single)
    println(doubleAverage(Observable(1, 2, 3.5)).toBlockingObservable.single)
  }
  
  @Test def testSum() {
    assertEquals(10, Observable(1, 2, 3, 4).sum.toBlockingObservable.single)
    assertEquals(6, Observable(4, 2).sum.toBlockingObservable.single)
    assertEquals(0, Observable[Int]().sum.toBlockingObservable.single)
  }
  
  @Test def testProduct() {
    assertEquals(24, Observable(1, 2, 3, 4).product.toBlockingObservable.single)
    assertEquals(8, Observable(4, 2).product.toBlockingObservable.single)
    assertEquals(1, Observable[Int]().product.toBlockingObservable.single)
  }
  
  @Test def mapWithIndexExample() {
    // We don't need mapWithIndex because we already have zipWithIndex, which we can easily
    // combine with map:
    Observable("a", "b", "c").zipWithIndex.map(pair => pair._1 + " has index " + pair._2)
        .toBlockingObservable.foreach(println(_))
        
    // Or even nicer with for-comprehension syntax:
    (for ((letter, index) <- Observable("a", "b", "c").zipWithIndex) yield letter + " has index " + index)
        .toBlockingObservable.foreach(println(_))
  }
  
  // source Observables are in a List:
  @Test def zipManySeqExample() {
    val observables = List(Observable(1, 2), Observable(10, 20), Observable(100, 200))
    (for (seq <- Observable.zip(observables)) yield seq.mkString("(", ", ", ")"))
        .toBlockingObservable.foreach(println(_))
  }
  
  // source Observables are in an Observable:
  @Test def zipManyObservableExample() {
    val observables = Observable(Observable(1, 2), Observable(10, 20), Observable(100, 200))
    (for (seq <- Observable.zip(observables)) yield seq.mkString("(", ", ", ")"))
        .toBlockingObservable.foreach(println(_))
  }
  
  @Test def takeFirstWithCondition() {
    val condition: Int => Boolean = _ >= 3
    assertEquals(3, Observable(1, 2, 3, 4).dropWhile(!condition(_)).take(1).toBlockingObservable.single)
  }
  
  def output(s: String): Unit = println(s)
  
  // blocks until obs has completed
  def waitFor[T](obs: Observable[T]): Unit = {
    obs.toBlockingObservable.toIterable.last
  }
  
}