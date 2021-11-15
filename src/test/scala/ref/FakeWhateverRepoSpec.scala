package com.agilogy.playground
package ref
import cats.implicits._
import cats.effect.{IO, Ref, Sync}
import weaver.SimpleIOSuite

object FakeWhateverRepoSpec extends SimpleIOSuite {
  test("can insert a whatever and list it") {
    for {
      repo <- FakeWhateverRepo.empty
      _ <- repo.insert(Whatever("hello"))
      list <- repo.list()
    } yield expect(list == List(Whatever("hello")))
  }

  test("it can program a response for a given method") {
    for {
      repo <- FakeWhateverRepo.empty
      _ <- repo.insertOp.addResponse(IO.raiseError(new RuntimeException))
      response <- repo.insert(Whatever("fail")).attempt
      default <- repo.insert(Whatever("work")).attempt
    } yield expect(response.isLeft) and expect(default.isRight)
  }

  test("it returns the responses in order") {
    for {
      repo <- FakeWhateverRepo.empty
      _ <- repo.listOp.addResponse(IO(List(Whatever("one"))))
      _ <- repo.listOp.addResponse(IO(List(Whatever("two"))))
      _ <- repo.listOp.addResponse(IO(List(Whatever("three"))))
      one <- repo.list()
      two <- repo.list()
      three <- repo.list()
    } yield expect(one == List(Whatever("one"))) and
      expect(two == List(Whatever("two"))) and
      expect(three == List(Whatever("three")))
  }

  test("it can be programmed to always return the same response") {
    for {
      repo <- FakeWhateverRepo.empty
      _ <- repo.insertOp.alwaysRespond(IO.raiseError(new RuntimeException))
      r1 <- repo.insert(Whatever("fail")).attempt
      r2 <- repo.insert(Whatever("fail")).attempt
      r3 <- repo.insert(Whatever("fail")).attempt
      _ <- repo.insertOp.alwaysRespond(IO(()))
      r4 <- repo.insert(Whatever("fail")).attempt
    } yield expect(r1.isLeft) and expect(r2.isLeft) and expect(r3.isLeft) and expect(r4.isRight)
  }

  test("it should not update the state if a method fails") {
    for {
      repo <- FakeWhateverRepo.empty
      _ <- repo.insertOp.addResponse(IO.raiseError(new RuntimeException))
      _ <- repo.insert(Whatever("fail")).attempt
      list <- repo.list()
    } yield expect(list.isEmpty)
  }

}

case class Whatever(message: String)
case class FakeWhateverRepoState(whatevers: List[Whatever] = List.empty)

class FakeWhateverRepo(state: Ref[IO, FakeWhateverRepoState],
                       insertResponses: Ref[IO, LazyList[IO[Unit]]],
                       listResponses: Ref[IO, LazyList[IO[List[Whatever]]]]) {

  val insertOp = MockedOperation(insertResponses, state) { _ => () }

  def insert(w: Whatever): IO[Unit] = insertOp.updateState(s => s.copy(whatevers = s.whatevers :+ w))

  val listOp = MockedOperation(listResponses, state)(s => s.whatevers)

  def list(): IO[List[Whatever]] = listOp.queryState
}

object FakeWhateverRepo {

  def empty: IO[FakeWhateverRepo] = for {
    stateRef <- Ref.of[IO, FakeWhateverRepoState](FakeWhateverRepoState())
    insertResponsesRef <- Ref.of[IO,LazyList[IO[Unit]]](LazyList.empty)
    listResponsesRef <- Ref.of[IO,LazyList[IO[List[Whatever]]]](LazyList.empty)
  } yield new FakeWhateverRepo(stateRef, insertResponsesRef, listResponsesRef)
}