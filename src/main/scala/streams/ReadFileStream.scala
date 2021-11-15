package com.agilogy.playground
package streams

import cats.effect.{IO, IOApp}
import fs2.{Chunk, Stream, text}

import java.io.InputStream

object ReadFileStream extends IOApp.Simple {
  // Let's create a stream that reads one file and writes it to the standard output
  private val resource = "/logback.xml"
  private val chunkSize = 20
  private def withLibraryStream = fs2.io.readInputStream(
    IO.delay(this.getClass.getResourceAsStream(resource)),
    chunkSize,
    true
  )

  private def customStream = {
    val inputStreamEffect = IO.delay(this.getClass.getResourceAsStream(resource))
    val bufferEffect = IO(new Array[Byte](chunkSize))
    def close(is: InputStream):IO[Unit] = IO.blocking(is.close())

    def readFromInputStream(is: InputStream): Stream[IO, Option[Chunk[Byte]]] = {
      Stream
        .eval(bufferEffect)                             // Create a new buffer
        .evalMap { buffer =>                            // Effect that reads the next segment from the file
          IO.blocking(is.read(buffer)).map { numBytes =>
            if (numBytes < 0) None                      // With None we can finish the stream
            else if (numBytes == 0) Some(Chunk.empty)
            else if (numBytes < chunkSize) Some(Chunk.array(buffer, 0, numBytes))
            else Some(Chunk.array(buffer))
          }
        }
        .repeat // Keep reading until we finish the file
    }

    Stream.bracket(inputStreamEffect)(close)  // Make sure that we close the file after reading
      .flatMap(readFromInputStream)           // Read the file contents and generate the chunks
      .unNoneTerminate                        // Terminate the stream if we return None
      .flatMap(c => Stream.chunk(c))          // Transform the chunk into values
  }

  override def run: IO[Unit] =
    customStream.through(text.utf8.decode).debug().compile.drain
}
