package uk.gov.hmrc.mobilehelptosave

import org.scalatest.{Assertion, Suite}
import play.api.libs.json.JsLookupResult
import org.scalatest.Matchers._

trait NumberVerification {

  def shouldBeBigDecimal(jsLookupResult: JsLookupResult, expectedValue: BigDecimal): Assertion = {
    // asOpt[String] is used to check numbers are formatted like "balance": 123.45 not "balance": "123.45"
    jsLookupResult.asOpt[String] shouldBe None
    jsLookupResult.as[BigDecimal] shouldBe expectedValue
  }
}
