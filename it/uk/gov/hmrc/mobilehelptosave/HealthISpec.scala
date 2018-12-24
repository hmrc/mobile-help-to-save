package uk.gov.hmrc.mobilehelptosave

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsObject, Json}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.mobilehelptosave.support.OneServerPerSuiteWsClient

/**
  * Check the health endpoints to ensure that they're wired correctly
  */
class HealthISpec extends WordSpec with Matchers
  with FutureAwaits with DefaultAwaitTimeout
  with OneServerPerSuiteWsClient {

  "GET /ping/ping" should {
    "return a 200 Ok response" in (await(wsUrl("/ping/ping").get()).status shouldBe 200)
  }

  "GET /admin/details" should {
    val url = wsUrl("/admin/details")
    "return a 200 Ok response" in (await(url.get()).status shouldBe 200)
    "return an empty object" in (Json.parse(await(url.get()).body) shouldBe JsObject(Seq()))
  }

}
