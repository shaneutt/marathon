package mesosphere.marathon
package state

import java.util.UUID
import monocle.macros.GenLens

case class InstanceSet(
  instances: Map[UUID, Instance]) {
  val instancesLens = GenLens[InstanceSet](_.instances)

  def withInstance(instance: Instance): InstanceSet = {
    instancesLens
      .modify { _.updated(instance.instanceId, instance) }
      .apply(this)
  }

  def withoutInstance(instanceId: UUID): InstanceSet = {
    instancesLens
      .modify { _ - instanceId }
      .apply(this)
  }
}

object InstanceSet {
  def empty = InstanceSet(Map.empty)
}
