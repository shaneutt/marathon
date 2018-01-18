package mesosphere.marathon

import akka.actor.{ ActorSystem, Scheduler }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.Timeout
import com.typesafe.config.{ Config, ConfigFactory }
import java.util.concurrent.TimeUnit
import org.scalatest.{ BeforeAndAfter, BeforeAndAfterAll, BeforeAndAfterEach, GivenWhenThen, Matchers, WordSpec, WordSpecLike }
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.ExecutionContextExecutor

trait UnitTestLike extends WordSpecLike
    with GivenWhenThen
    with ScalaFutures
    with BeforeAndAfter
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers {
}

trait UnitTest extends WordSpec with UnitTestLike

trait AkkaUnitTestLike extends UnitTestLike {
  protected lazy val akkaConfig: Config = ConfigFactory.parseString(
    s"""
      |akka.test.default-timeout=${patienceConfig.timeout.millisPart}
    """.stripMargin).withFallback(ConfigFactory.load())
  implicit lazy val system: ActorSystem = {
    ActorSystem(suiteName, akkaConfig)
  }
  implicit lazy val scheduler: Scheduler = system.scheduler
  implicit lazy val mat: Materializer = ActorMaterializer()
  implicit lazy val ctx: ExecutionContextExecutor = system.dispatcher
  implicit val askTimeout: Timeout = Timeout(patienceConfig.timeout.toMillis, TimeUnit.MILLISECONDS)

  abstract override def afterAll(): Unit = {
    super.afterAll()
    // intentionally shutdown the actor system last.
    system.terminate().futureValue
  }
}
