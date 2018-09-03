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

import org.scalatest.{Matchers, WordSpec}
import play.api.Application
import play.api.libs.json.JsUndefined
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub}
import uk.gov.hmrc.mobilehelptosave.support.{MongoTestCollectionsDropAfterAll, OneServerPerSuiteWsClient, WireMockSupport}

class StartupISpec extends WordSpec with Matchers
  with FutureAwaits with DefaultAwaitTimeout
  with WireMockSupport with MongoTestCollectionsDropAfterAll
  with OneServerPerSuiteWsClient with NumberVerification  {

  override implicit lazy val app: Application = appBuilder
    .configure(
      "helpToSave.balanceEnabled" -> true,
      "helpToSave.paidInThisMonthEnabled" -> true,
      "helpToSave.firstBonusEnabled" -> true
    )
    .build()

  private val generator = new Generator(0)
  private val nino = generator.nextNino

  "GET /mobile-help-to-save/startup" should {

    "return enabled=true for backwards compatibility until we do NGC-3244" in {
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsNotEnrolled()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "enabled").as[Boolean] shouldBe true
    }

    "include user.state and user.account" in {
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.accountExists(nino)

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe Some("Enrolled")

      (response.json \ "user" \ "account" \ "number").as[String] shouldBe "1000000000001"
      (response.json \ "user" \ "account" \ "openedYearMonth").as[String] shouldBe "2018-01"
      (response.json \ "user" \ "account" \ "isClosed").as[Boolean] shouldBe false
      (response.json \ "user" \ "account" \ "blocked" \ "unspecified").as[Boolean] shouldBe false
      shouldBeBigDecimal(response.json \ "user" \ "account" \ "balance", BigDecimal("123.45"))
      shouldBeBigDecimal(response.json \ "user" \ "account" \ "paidInThisMonth", BigDecimal("27.88"))
      shouldBeBigDecimal(response.json \ "user" \ "account" \ "canPayInThisMonth", BigDecimal("22.12"))
      shouldBeBigDecimal(response.json \ "user" \ "account" \ "maximumPaidInThisMonth", BigDecimal(50))
      // Date used for testing is a date when BST applied to test that the
      // service still returns the date supplied by NS&I unmodified during
      // BST.
      (response.json \ "user" \ "account" \ "thisMonthEndDate").as[String] shouldBe "2018-04-30"
      (response.json \ "user" \ "account" \ "nextPaymentMonthStartDate").as[String] shouldBe "2018-05-01"

      (response.json \ "user" \ "account" \ "accountHolderName").as[String] shouldBe "Testfore Testsur"
      (response.json \ "user" \ "account" \ "accountHolderEmail").as[String] shouldBe "testemail@example.com"

      val firstBonusTermJson = (response.json \ "user" \ "account" \ "bonusTerms") (0)
      shouldBeBigDecimal(firstBonusTermJson \ "bonusEstimate", BigDecimal("90.99"))
      shouldBeBigDecimal(firstBonusTermJson \ "bonusPaid", BigDecimal("90.99"))
      (firstBonusTermJson \ "endDate").as[String] shouldBe "2019-12-31"
      (firstBonusTermJson \ "bonusPaidOnOrAfterDate").as[String] shouldBe "2020-01-01"

      val secondBonusTermJson = (response.json \ "user" \ "account" \ "bonusTerms") (1)
      shouldBeBigDecimal(secondBonusTermJson \ "bonusEstimate", BigDecimal(12))
      shouldBeBigDecimal(secondBonusTermJson \ "bonusPaid", BigDecimal(0))
      (secondBonusTermJson \ "endDate").as[String] shouldBe "2021-12-31"
      (secondBonusTermJson \ "bonusPaidOnOrAfterDate").as[String] shouldBe "2022-01-01"
    }

    "include account closure fields when account is closed" in {
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.closedAccountExists(nino)

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe Some("Enrolled")

      (response.json \ "user" \ "account" \ "number").as[String] shouldBe "1000000000002"
      (response.json \ "user" \ "account" \ "openedYearMonth").as[String] shouldBe "2018-03"

      (response.json \ "user" \ "account" \ "isClosed").as[Boolean] shouldBe true
      (response.json \ "user" \ "account" \ "closureDate").as[String] shouldBe "2018-04-09"
      shouldBeBigDecimal(response.json \ "user" \ "account" \ "closingBalance", 10)

      (response.json \ "user" \ "account" \ "blocked" \ "unspecified").as[Boolean] shouldBe false
      shouldBeBigDecimal(response.json \ "user" \ "account" \ "balance", 0)
      shouldBeBigDecimal(response.json \ "user" \ "account" \ "paidInThisMonth", 0)
      shouldBeBigDecimal(response.json \ "user" \ "account" \ "canPayInThisMonth", 50)
      shouldBeBigDecimal(response.json \ "user" \ "account" \ "maximumPaidInThisMonth", 50)
      (response.json \ "user" \ "account" \ "thisMonthEndDate").as[String] shouldBe "2018-04-30"
      (response.json \ "user" \ "account" \ "nextPaymentMonthStartDate").as[String] shouldBe "2018-05-01"

      val firstBonusTermJson = (response.json \ "user" \ "account" \ "bonusTerms") (0)
      shouldBeBigDecimal(firstBonusTermJson \ "bonusEstimate", BigDecimal("7.50"))
      shouldBeBigDecimal(firstBonusTermJson \ "bonusPaid", BigDecimal(0))
      (firstBonusTermJson \ "endDate").as[String] shouldBe "2020-02-29"
      (firstBonusTermJson \ "bonusPaidOnOrAfterDate").as[String] shouldBe "2020-03-01"

      val secondBonusTermJson = (response.json \ "user" \ "account" \ "bonusTerms") (1)
      shouldBeBigDecimal(secondBonusTermJson \ "bonusEstimate", BigDecimal(0))
      shouldBeBigDecimal(secondBonusTermJson \ "bonusPaid", BigDecimal(0))
      (secondBonusTermJson \ "endDate").as[String] shouldBe "2022-02-28"
      (secondBonusTermJson \ "bonusPaidOnOrAfterDate").as[String] shouldBe "2022-03-01"
    }

    "omit user state if call to help-to-save fails" in {
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.enrolmentStatusReturnsInternalServerError()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe None
      (response.json \ "userError" \ "code").as[String] shouldBe "GENERAL"
      // check that only the user field has been omitted, not all fields
      (response.json \ "enabled").asOpt[Boolean] should not be None
      (response.json \ "infoUrl").asOpt[String] should not be None
    }

    "omit account details but still include user state if call to get account fails" in {
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.accountReturnsInternalServerError(nino)

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe Some("Enrolled")
      (response.json \ "user" \ "account") shouldBe a[JsUndefined]
      (response.json \ "user" \ "accountError" \ "code").as[String] shouldBe "GENERAL"
    }

    "omit account details but still include user state if get account returns JSON that doesn't conform to the schema" in {
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.accountReturnsInvalidJson(nino)

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe Some("Enrolled")
      (response.json \ "user" \ "account") shouldBe a[JsUndefined]
      (response.json \ "user" \ "accountError" \ "code").as[String] shouldBe "GENERAL"
    }

    "omit account details but still include user state if get account returns badly formed JSON" in {
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.accountReturnsBadlyFormedJson(nino)

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe Some("Enrolled")
      (response.json \ "user" \ "account") shouldBe a[JsUndefined]
      (response.json \ "user" \ "accountError" \ "code").as[String] shouldBe "GENERAL"
    }

    "return 401 when the user is not logged in" in {
      AuthStub.userIsNotLoggedIn()
      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 401
      response.body shouldBe "Authorisation failure [Bearer token not supplied]"
    }

    "return 403 Forbidden when the user is logged in with an insufficient confidence level" in {
      AuthStub.userIsLoggedInWithInsufficientConfidenceLevel()
      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 403
      response.body shouldBe "Authorisation failure [Insufficient ConfidenceLevel]"
    }

    //TODO
    "return 403 when the user is logged in with an auth provider that does not provide an internalId" in {
      AuthStub.userIsLoggedInButNotWithGovernmentGatewayOrVerify()
      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 403
      response.body shouldBe "Authorisation failure [UnsupportedAuthProvider]"
    }
  }
}