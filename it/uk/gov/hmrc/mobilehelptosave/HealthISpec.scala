package uk.gov.hmrc.mobilehelptosave

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.mobilehelptosave.support.OneServerPerSuiteWsClient

/**
  * Check the health endpoints to ensure that they're wired correctly
  */
class HealthISpec
    extends AnyWordSpecLike
    with Matchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with OneServerPerSuiteWsClient {

  "GET /ping/ping" should {
    "return a 200 Ok response" in (await(wsUrl("/ping/ping").get()).status shouldBe 200)
  }
}
