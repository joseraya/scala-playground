package com.agilogy.playground
package streams

import cats.{Applicative, Functor}
import cats.effect.{IO, IOApp, Sync}
import cats.implicits._
import cats.syntax._
import fs2._

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
/*
These are the solutions to the exercises here: https://fs2.io/#/guide?id=exercises-stream-building
 */
object StreamsBuildingExercises extends IOApp.Simple {

  // Very basic stream
  private val counter = new AtomicInteger(0)
  def nextValue : IO[Int] = IO(counter.incrementAndGet())
  def basicStream: IO[Unit] = Stream.eval(nextValue) // This is the function that will be evaluated
    .repeat // Concatenate the stream to itself so that it becomes actually "infinite"
    .take(5)  // Limit the number of elements that we are going to take
    .debug()  // Print the values that have been generated
    .compile  // We are done configuring the stream, let's bring the operations to consume the values
    .drain    // Just run the stream to perform the side effects and "ignore" the values

  // Custom repeat operation
  private def customRepeat[F[_],O](s: Stream[F,O]): Stream[F,O] = s ++ customRepeat(s)
  private def customRepeatStream = customRepeat(Stream.eval(nextValue))
    .take(4).debug()
    .compile.drain

  // Custom drain
  private def customDrain[F[_],O](s: Stream.CompileOps[F, F, O]): F[Unit] = s.fold(())((_,_) => ())
  private def customDrainStream: IO[Unit] = customDrain(
    Stream.eval(nextValue).repeat.take(5).debug().compile
  )

  // Custom exec
  def customExec[F[_]:Functor,O](effect: F[O]): Stream[F, Unit] = Stream.eval(effect.void)
  private def customExecStream = customExec(IO(println("Hello!")))

  // Custom attempt
  private def customAttempt[F[_]: Applicative, O](s: Stream[F,O]): Stream[F, Either[Throwable, O]] = s
    .map(value => Right(value))
    .handleErrorWith(t => Stream.eval(Left(t).pure[F]))

  private def customAttemptStream: Stream[IO, Either[Throwable, Int]] = {
    customAttempt(
      Stream.evals(IO(List(1,2))).append(
        Stream.eval(IO.raiseError[Int](new Exception("nooo!!!")))
      )
    )
  }

  override def run: IO[Unit] = customAttemptStream.take(5).debug().compile.drain

}
