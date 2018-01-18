package mesosphere.marathon
package repository

import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{ Keep, Sink, Source }
import mesosphere.marathon.state.RunSpec
import org.scalatest.Inside

class StateAuthorityTest extends AkkaUnitTestLike with Inside {
  "lol" in {
    val requestId = 1011
    Given("a fresh instance of Marathon")
    val (input, result) = Source.queue[StateAuthorityInputEvent](16, OverflowStrategy.fail)
      .via(StateAuthority.commandProcessorFlow)
      .toMat(Sink.queue())(Keep.both)
      .run

    When("I submit a command to update some app")
    input.offer(CommandRequest(requestId, StateCommand.PutApp(RunSpec("/lol", "blue", "2018-01-01", "sleep 100"))))

    Then("an effect to persist these updates is emitted")
    inside(result.pull().futureValue) {
      case Some(update: Effect.PersistUpdates) =>
        update.version shouldBe 2
        inside(update.updates) {
          case Seq(update: StateTransition.RunSpecUpdated) =>
            update.ref.id shouldBe("/lol")
        }
    }


    When("the storage layer confirms the persistence")
    input.offer(MarkPersisted(2))

    Then("the updates are published")
    inside(result.pull().futureValue) {
      case Some(Effect.PublishUpdates(updates)) =>
        val Seq(update: StateTransition.RunSpecUpdated) = updates
        update.ref.id shouldBe ("/lol")
    }

    And("the client gets acknowledged")
    inside(result.pull().futureValue) {
      case Some(result: Effect.PublishResult) =>
        result.requestId shouldBe requestId
        inside(result.result) {
          case Right(result) =>
            result.stateTransitions.size shouldBe 1
        }
    }

    When("we close the stream")
    input.complete()

    Then("no further events are generated")
    result.pull().futureValue shouldBe None
  }
}
