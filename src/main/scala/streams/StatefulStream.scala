package com.agilogy.playground
package streams

import cats.effect.{IO, IOApp, Ref}

/*
This example shows how to create a stream that invokes a function until a given condition is met.
 */
object StatefulStream extends IOApp.Simple {

  def nextValue(ref: Ref[IO, Int]): IO[Int] = ref.getAndUpdate(current => current + 1)
  // We want to create a stream that calls 'nextValue' until it returns 0

  def stream: fs2.Stream[IO, Int] = {

    def readValues(ref: Ref[IO, Int]): fs2.Stream[IO, Int] =
      fs2.Stream.eval(nextValue(ref).map { result =>
        if (result == 5) None
        else Some(result)
      })
        .repeat           // This is mostly the trick: With repeat we keep calling the function
        .unNoneTerminate  // This is the second part of the trick: If the function returns None, terminate the stream

    fs2.Stream
      .eval(Ref.of[IO, Int](1)) // This only happens once (acquire the resources, intialize the state, etc.)
      .flatMap(readValues)      // If we `.repeat` this stream then we would be creating a new ref on each iteration
  }

  override def run: IO[Unit] = stream.debug().compile.drain
}
