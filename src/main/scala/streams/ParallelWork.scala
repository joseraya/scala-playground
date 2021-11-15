package com.agilogy.playground
package streams
import cats.effect.std.Random
import fs2.{Pipe, Stream}
import cats.effect.{IO, IOApp}
import org.typelevel.log4cats.Logger
import cats.implicits._
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.{DurationInt, DurationLong}

object ParallelWork extends IOApp.Simple {
  val logger = Slf4jLogger.getLogger[IO]

  private val counter = new AtomicInteger(0)
  private def csvRows: Stream[IO, String] = Stream.eval(IO("%07d".format(counter.incrementAndGet()))).repeat.take(10)

  private def writeToPostgres(row: String) =
    if (row == "fail") {
      IO.raiseError(new RuntimeException("Error!"))
    } else {
      IO.sleep(1.seconds) >> logger.info(s"Write $row to postgres")
    }

  private def writeToCassandra(row: String) =
      IO.sleep(2.seconds) >> logger.info(s"Write $row to cassandra")


  private def writeToBoth(row: String) = for {
    _ <- writeToCassandra(row)
    _ <- writeToPostgres(row)
  } yield ()

  private def writeToBothFibers(row: String) = for {
    cf <- writeToCassandra(row).start
    pf <- writeToPostgres(row).start
    _ <- cf.join
    _ <- pf.join
  } yield ()

  private def writeToBothParmap(row: String) =
    (writeToCassandra(row),  writeToPostgres(row)).parMapN((_ ,_) => ())

  private def writeToCassandraPipe: Pipe[IO, String, Unit] = stream => {
    stream.evalMap(writeToCassandra)
  }

  private def writeToPostgresPipe: Pipe[IO, String, Unit] = stream => {
    stream.evalMap(writeToPostgres)
  }

  private def time[A](action: IO[A]) = for {
    start <- IO(System.currentTimeMillis())
    result <- action
    end <- IO(System.currentTimeMillis())
    _ <- logger.info(s"Effect took ${(end-start).millis} to complete")
  } yield result

  //Takes about 20 seconds
  private def broadcastPipeline = csvRows.broadcastThrough(writeToCassandraPipe, writeToPostgresPipe)

  //Takes about 20 seconds
  private def pipelineBothFibers = csvRows.evalMap(writeToBothFibers)

  //Takes about 20 seconds
  private def pipelineBothParmap = csvRows.evalMap(writeToBothParmap)

  //Takes about 30 seconds
  private def pipelineBothFlatmap = csvRows.evalMap(writeToBoth)

  //Takes about 7 seconds
  private def parallelPipeline = csvRows.parEvalMap(5)(writeToBoth)
  def run: IO[Unit] = time(parallelPipeline.compile.drain)
}
