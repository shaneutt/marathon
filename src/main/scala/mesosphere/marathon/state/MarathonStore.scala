package mesosphere.marathon.state

import com.google.protobuf.InvalidProtocolBufferException
import org.apache.mesos.state.State
import scala.collection.JavaConverters._
import scala.concurrent._
import scala.concurrent.duration.Duration
import mesosphere.marathon.StorageException

/**
 * @author Tobi Knaup
 */

class MarathonStore[S <: MarathonState[_]](state: State,
                       newState: () => S, prefix:String = "app:") extends PersistenceStore[S] {

  val defaultWait = Duration(3, "seconds")

  import ExecutionContext.Implicits.global
  import mesosphere.util.BackToTheFuture._

  def fetch(key: String): Future[Option[S]] = {
    state.fetch(prefix + key) map {
      case Some(variable) => stateFromBytes(variable.value)
      case None => throw new StorageException(s"Failed to read $key")
    }
  }

  def modify(key: String)(f: S => S): Future[Option[S]] = {
    state.fetch(prefix + key) flatMap {
      case Some(variable) =>
        val fetched = stateFromBytes(variable.value).getOrElse(newState())
        state.store(variable.mutate(f(fetched).toProtoByteArray)) map {
          case Some(newVar) => stateFromBytes(newVar.value)
          case None => throw new StorageException(s"Failed to store $key")
        }
      case None => throw new StorageException(s"Failed to read $key")
    }
  }

  def expunge(key: String): Future[Boolean] = {
    state.fetch(prefix + key) flatMap {
      case Some(variable) =>
        state.expunge(variable) map {
          case Some(b) => b
          case None => throw new StorageException(s"Failed to expunge $key")
        }
      case None => throw new StorageException(s"Failed to read $key")
    }
  }

  def names(): Future[Iterator[String]] = {
    // TODO use implicit conversion after it has been merged
    future {
      try {
        state.names().get.asScala.collect {
          case name if name startsWith prefix =>
            name.replaceFirst(prefix, "")
        }
      } catch {
        // Thrown when node doesn't exist
        case e: ExecutionException => Seq().iterator
      }
    }
  }

  private def stateFromBytes(bytes: Array[Byte]): Option[S] = {
    try {
      Some(newState().mergeFromProto(bytes))
    }
    catch {
      case e: InvalidProtocolBufferException => None
    }
  }
}
