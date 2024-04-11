package uk.gov.hmrc.mobilehelptosave

import uk.gov.hmrc.mobilehelptosave.support.BaseISpec

/**
  * Check the health endpoints to ensure that they're wired correctly
  */
class HealthISpec extends BaseISpec {

  "GET /ping/ping" should {
    "return a 200 Ok response" in (await(wsUrl("/ping/ping").get()).status shouldBe 200)
  }
}
