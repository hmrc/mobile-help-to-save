package uk.gov.hmrc.mobilehelptosave.support

import org.scalatestplus.play.components.WithApplicationComponents
import uk.gov.hmrc.mobilehelptosave.wiring.ServiceComponents

trait ComponentSupport {
  self: WithApplicationComponents =>
  override val components: ServiceComponents = new ServiceComponents(context)
}
