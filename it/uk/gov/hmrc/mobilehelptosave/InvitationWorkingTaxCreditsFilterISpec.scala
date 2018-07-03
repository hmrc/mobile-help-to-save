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

import org.joda.time.DateTime
import org.scalatest.{Matchers, WordSpec}
import play.api.Application
import play.api.inject.bind
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mobilehelptosave.services.{Clock, FixedFakeClock}
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub, TaxCreditBrokerStub}
import uk.gov.hmrc.mobilehelptosave.support.{MongoTestCollectionsDropAfterAll, OneServerPerSuiteWsClient, WireMockSupport}

class InvitationWorkingTaxCreditsFilterISpec extends WordSpec with Matchers
  with FutureAwaits with DefaultAwaitTimeout with InvitationCleanup
  with WireMockSupport with MongoTestCollectionsDropAfterAll with OneServerPerSuiteWsClient {

  private val fixedClock = new FixedFakeClock(DateTime.parse("2018-02-08T12:34:56.000Z"))

  override implicit lazy val app: Application = appBuilder
    .configure(
      InvitationConfig.Enabled,
      "helpToSave.invitationFilters.workingTaxCredits" -> "true"
    )
    .overrides(bind[Clock].toInstance(fixedClock))
    .build()

  private val generator = new Generator(0)
  private val nino = generator.nextNino

  "GET /mobile-help-to-save/startup" when {
    "only WTC filter is enabled" should {

      "return user.state = NotEnrolled when user is not already enrolled and has not received a WTC payment recently" in {
        AuthStub.userIsLoggedIn(internalAuthId, nino)
        HelpToSaveStub.currentUserIsNotEnrolled()
        TaxCreditBrokerStub.userHasNoWorkingTaxCreditsPayments(nino)

        val response = await(wsUrl("/mobile-help-to-save/startup").get())
        response.status shouldBe 200
        (response.json \ "user" \ "state").asOpt[String] shouldBe Some("NotEnrolled")
      }

      """return user.state = NotEnrolled when user is not already enrolled and tax-credits-broker returns {"excluded":true}""" in {
        AuthStub.userIsLoggedIn(internalAuthId, nino)
        HelpToSaveStub.currentUserIsNotEnrolled()
        TaxCreditBrokerStub.paymentSummaryReturnsExcluded(nino)

        val response = await(wsUrl("/mobile-help-to-save/startup").get())
        response.status shouldBe 200
        (response.json \ "user" \ "state").asOpt[String] shouldBe Some("NotEnrolled")
      }

      "return user.state = InvitedFirstTime and then user.state = Invited when user is not already enrolled and has received a WTC payment recently" in {
        AuthStub.userIsLoggedIn(internalAuthId, nino)
        HelpToSaveStub.currentUserIsNotEnrolled()
        TaxCreditBrokerStub.userHasFeb2018WorkingTaxCreditsPayments(nino)

        val response1 = await(wsUrl("/mobile-help-to-save/startup").get())
        response1.status shouldBe 200
        (response1.json \ "user" \ "state").asOpt[String] shouldBe Some("InvitedFirstTime")

        val response2 = await(wsUrl("/mobile-help-to-save/startup").get())
        response2.status shouldBe 200
        (response2.json \ "user" \ "state").asOpt[String] shouldBe Some("Invited")
      }

      "omit user state if call to tax-credits-broker to get payments fails" in {
        AuthStub.userIsLoggedIn(internalAuthId, nino)
        HelpToSaveStub.currentUserIsNotEnrolled()
        TaxCreditBrokerStub.gettingTaxCreditsPaymentsReturnsInternalServerError(nino)

        val response = await(wsUrl("/mobile-help-to-save/startup").get())
        response.status shouldBe 200
        (response.json \ "user" \ "state").asOpt[String] shouldBe None
        // check that only the user state field has been omitted, not all fields
        (response.json \ "enabled").asOpt[Boolean] should not be None
        (response.json \ "infoUrl").asOpt[String] should not be None
      }

      """Only call tax-credits-broker once when multiple requests are made for an ineligible NINO (to reduce load on HoD)""" in {
        AuthStub.userIsLoggedIn(internalAuthId, nino)
        HelpToSaveStub.currentUserIsNotEnrolled()
        TaxCreditBrokerStub.paymentSummaryReturnsExcluded(nino)

        val response1 = await(wsUrl("/mobile-help-to-save/startup").get())
        response1.status shouldBe 200
        (response1.json \ "user" \ "state").asOpt[String] shouldBe Some("NotEnrolled")

        TaxCreditBrokerStub.paymentSummaryShouldOnlyHaveBeenCalledOnce(nino)

        val response2 = await(wsUrl("/mobile-help-to-save/startup").get())
        response2.status shouldBe 200
        (response2.json \ "user" \ "state").asOpt[String] shouldBe Some("NotEnrolled")

        TaxCreditBrokerStub.paymentSummaryShouldOnlyHaveBeenCalledOnce(nino)
      }
    }
  }
}
