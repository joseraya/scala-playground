package com.agilogy.playground
package ref

import cats.effect.{Ref, Sync}
import cats.implicits._
case class MockedOperation[R, S, F[_]: Sync](
                                              responsesRef: Ref[F, LazyList[F[R]]],
                                              stateRef: Ref[F, S])(defaultResponse: S => R) {

  def addResponse(value: F[R]): F[Unit] = {
    responsesRef.update { responses =>
      responses :+ value
    }
  }

  def alwaysRespond(value: F[R]): F[Unit] = responsesRef.update( _ => LazyList.continually(value))

  def updateState(fn: S => S): F[R] = for {
    // Atomically remove the response so that no other method can get it
    // If the list was empty drop(1) will return another empty list
    responses <- responsesRef.getAndUpdate(_.drop(1))
    result <- updateState(fn, responses.headOption)
  } yield result

  private def updateState(update: S => S, maybeResponse: Option[F[R]]): F[R] = maybeResponse match {
    case Some(response) =>
      // In this case we don't need the state to calculate the response, we can evaluate the response
      // first (so that the method fails without updating the state)
      for {
        result <- response
        _ <- stateRef.updateAndGet(update)
      } yield result
    case None =>
      stateRef.updateAndGet(update).map(defaultResponse)
  }

  // Invoke this method if we just want a response but don't want to modify the state
  def queryState: F[R] = for {
    responses <- responsesRef.getAndUpdate(_.drop(1))
    state <- stateRef.get
    result <- responses.headOption.getOrElse(defaultResponse(state).pure[F])
  } yield result

}
