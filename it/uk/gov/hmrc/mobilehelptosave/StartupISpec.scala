/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.mobilehelptosave

import org.scalatest.{Assertion, Matchers, WordSpec}
import play.api.Application
import play.api.libs.json.{JsLookupResult, JsUndefined}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveProxyStub, HelpToSaveStub}
import uk.gov.hmrc.mobilehelptosave.support.{OneServerPerSuiteWsClient, WireMockSupport}

class StartupISpec extends WordSpec with Matchers
  with FutureAwaits with DefaultAwaitTimeout with InvitationCleanup
  with WireMockSupport with OneServerPerSuiteWsClient {

  override implicit lazy val app: Application = wireMockApplicationBuilder()
    .configure(
      InvitationConfig.Enabled,
      "helpToSave.invitationFilters.survey" -> "false",
      "helpToSave.invitationFilters.workingTaxCredits" -> "false"
    )
    .build()

  private val generator = new Generator(0)
  private val nino = generator.nextNino

  "GET /mobile-help-to-save/startup" should {

    "include user.state and user.account" in {
      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveProxyStub.nsiAccountExists(nino)

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe Some("Enrolled")
      (response.json \ "user" \ "account" \ "isClosed").as[Boolean] shouldBe false
      shouldBeBigDecimal(response.json \ "user" \ "account" \ "balance", BigDecimal("123.45"))
      shouldBeBigDecimal(response.json \ "user" \ "account" \ "paidInThisMonth", BigDecimal("27.88"))
      shouldBeBigDecimal(response.json \ "user" \ "account" \ "canPayInThisMonth", BigDecimal("22.12"))
      shouldBeBigDecimal(response.json \ "user" \ "account" \ "maximumPaidInThisMonth", BigDecimal(50))
      (response.json \ "user" \ "account" \ "thisMonthEndDate").as[String] shouldBe "2018-02-28"

      val firstBonusTermJson = (response.json \ "user" \ "account" \ "bonusTerms")(0)
      shouldBeBigDecimal(firstBonusTermJson \ "bonusEstimate", BigDecimal("90.99"))
      shouldBeBigDecimal(firstBonusTermJson \ "bonusPaid", BigDecimal("90.99"))
      (firstBonusTermJson \ "bonusPaidOnOrAfterDate").as[String] shouldBe "2020-01-01"

      val secondBonusTermJson = (response.json \ "user" \ "account" \ "bonusTerms")(1)
      shouldBeBigDecimal(secondBonusTermJson \ "bonusEstimate", BigDecimal(12))
      shouldBeBigDecimal(secondBonusTermJson \ "bonusPaid", BigDecimal(0))
      (secondBonusTermJson \ "bonusPaidOnOrAfterDate").as[String] shouldBe "2022-01-01"
    }

    "include account closure fields when account is closed" in {
      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveProxyStub.closedNsiAccountExists(nino)

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe Some("Enrolled")

      (response.json \ "user" \ "account" \ "isClosed").as[Boolean] shouldBe true
      (response.json \ "user" \ "account" \ "closureDate").as[String] shouldBe "2018-04-09"
      shouldBeBigDecimal(response.json \ "user" \ "account" \ "closingBalance", 10)

      shouldBeBigDecimal(response.json \ "user" \ "account" \ "balance", 0)
      shouldBeBigDecimal(response.json \ "user" \ "account" \ "paidInThisMonth", 0)
      shouldBeBigDecimal(response.json \ "user" \ "account" \ "canPayInThisMonth", 50)
      shouldBeBigDecimal(response.json \ "user" \ "account" \ "maximumPaidInThisMonth", 50)
      // Date used for testing is a date when BST applied to test that the
      // service still returns the date supplied by NS&I unmodified during
      // BST.
      (response.json \ "user" \ "account" \ "thisMonthEndDate").as[String] shouldBe "2018-04-30"

      val firstBonusTermJson = (response.json \ "user" \ "account" \ "bonusTerms")(0)
      shouldBeBigDecimal(firstBonusTermJson \ "bonusEstimate", BigDecimal("7.50"))
      shouldBeBigDecimal(firstBonusTermJson \ "bonusPaid", BigDecimal(0))
      (firstBonusTermJson \ "bonusPaidOnOrAfterDate").as[String] shouldBe "2020-03-01"

      val secondBonusTermJson = (response.json \ "user" \ "account" \ "bonusTerms")(1)
      shouldBeBigDecimal(secondBonusTermJson \ "bonusEstimate", BigDecimal(0))
      shouldBeBigDecimal(secondBonusTermJson \ "bonusPaid", BigDecimal(0))
      (secondBonusTermJson \ "bonusPaidOnOrAfterDate").as[String] shouldBe "2022-03-01"
    }

    "integrate with the metrics returned by /admin/metrics" in {
      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsNotEnrolled()

      def invitationCountMetric(): Integer = {
        val metricsResponse = await(wsUrl("/admin/metrics").get())
        (metricsResponse.json \ "counters" \ "invitation" \ "count").as[Int]
      }

      val invitationCountBefore = invitationCountMetric()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe Some("InvitedFirstTime")

      val invitationCountAfter = invitationCountMetric()

      (invitationCountAfter - invitationCountBefore) shouldBe 1
    }

    "omit user state if call to help-to-save fails" in {
      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.enrolmentStatusReturnsInternalServerError()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe None
      // check that only the user state field has been omitted, not all fields
      (response.json \ "enabled").asOpt[Boolean] should not be None
      (response.json \ "infoUrl").asOpt[String] should not be None
    }

    "omit account details but still include user state if call to get account fails" in {
      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveProxyStub.nsiAccountReturnsInternalServerError()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe Some("Enrolled")
      (response.json \ "user" \ "account") shouldBe a [JsUndefined]
    }

    "omit account details but still include user state if get account returns JSON that doesn't conform to the schema" in {
      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveProxyStub.nsiAccountReturnsInvalidAccordingToSchemaJson(nino)

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe Some("Enrolled")
      (response.json \ "user" \ "account") shouldBe a [JsUndefined]
    }

    "omit account details but still include user state if get account returns badly formed JSON" in {
      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveProxyStub.nsiAccountReturnsBadlyFormedJson(nino)

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe Some("Enrolled")
      (response.json \ "user" \ "account") shouldBe a [JsUndefined]
    }

    "return 401 when the user is not logged in" in {
      AuthStub.userIsNotLoggedIn()
      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 401
    }

    "return 403 when the user is logged in with an auth provider that does not provide an internalId" in {
      AuthStub.userIsLoggedInButNotWithGovernmentGatewayOrVerify()
      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 403
    }
  }

  private def shouldBeBigDecimal(jsLookupResult: JsLookupResult, expectedValue: BigDecimal): Assertion = {
    // asOpt[String] is used to check numbers are formatted like "balance": 123.45 not "balance": "123.45"
    jsLookupResult.asOpt[String] shouldBe None
    jsLookupResult.as[BigDecimal] shouldBe expectedValue
  }
}
