
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

import org.scalatest._
import play.api.Application
import play.api.libs.json.JsObject
import play.api.libs.ws.WSResponse
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mobilehelptosave.scalatest.SchemaMatchers
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub}
import uk.gov.hmrc.mobilehelptosave.support.{MongoTestCollectionsDropAfterAll, OneServerPerSuiteWsClient, WireMockSupport}

class AccountsISpec extends WordSpec with Matchers
  with SchemaMatchers with TransactionTestData
  with FutureAwaits with DefaultAwaitTimeout
  with WireMockSupport with MongoTestCollectionsDropAfterAll
  with OneServerPerSuiteWsClient with NumberVerification {

  override implicit lazy val app: Application = appBuilder.build()

  private val generator = new Generator(0)
  private val nino = generator.nextNino

  "GET /savings-account/{nino}" should {

    "respond with 200 and the users account data" in {

      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.accountExists(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino").get())

      response.status shouldBe 200

      (response.json \ "number").as[String] shouldBe "1000000000001"
      (response.json \ "openedYearMonth").as[String] shouldBe "2018-01"
      (response.json \ "isClosed").as[Boolean] shouldBe false
      (response.json \ "blocked" \ "unspecified").as[Boolean] shouldBe false
      shouldBeBigDecimal(response.json \ "balance", BigDecimal("123.45"))
      shouldBeBigDecimal(response.json \ "paidInThisMonth", BigDecimal("27.88"))
      shouldBeBigDecimal(response.json \ "canPayInThisMonth", BigDecimal("22.12"))
      shouldBeBigDecimal(response.json \ "maximumPaidInThisMonth", BigDecimal(50))
      (response.json \ "thisMonthEndDate").as[String] shouldBe "2018-04-30"
      (response.json \ "nextPaymentMonthStartDate").as[String] shouldBe "2018-05-01"

      (response.json \ "accountHolderName").as[String] shouldBe "Testfore Testsur"
      (response.json \ "accountHolderEmail").as[String] shouldBe "testemail@example.com"

      val firstBonusTermJson = (response.json \ "bonusTerms") (0)
      shouldBeBigDecimal(firstBonusTermJson \ "bonusEstimate", BigDecimal("90.99"))
      shouldBeBigDecimal(firstBonusTermJson \ "bonusPaid", BigDecimal("90.99"))
      (firstBonusTermJson \ "endDate").as[String] shouldBe "2019-12-31"
      (firstBonusTermJson \ "bonusPaidOnOrAfterDate").as[String] shouldBe "2020-01-01"

      val secondBonusTermJson = (response.json \ "bonusTerms") (1)
      shouldBeBigDecimal(secondBonusTermJson \ "bonusEstimate", BigDecimal(12))
      shouldBeBigDecimal(secondBonusTermJson \ "bonusPaid", BigDecimal(0))
      (secondBonusTermJson \ "endDate").as[String] shouldBe "2021-12-31"
      (secondBonusTermJson \ "bonusPaidOnOrAfterDate").as[String] shouldBe "2022-01-01"
    }

    "respond with 200 and accountHolderEmail omitted when no email address are return from help to save" in {
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.accountExistsWithNoEmail(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino").get())

      response.status shouldBe 200

      (response.json \ "number").as[String] shouldBe "1000000000001"
      (response.json \ "openedYearMonth").as[String] shouldBe "2018-01"
      (response.json \ "isClosed").as[Boolean] shouldBe false
      (response.json \ "blocked" \ "unspecified").as[Boolean] shouldBe false
      shouldBeBigDecimal(response.json \ "balance", BigDecimal("123.45"))
      shouldBeBigDecimal(response.json \ "paidInThisMonth", BigDecimal("27.88"))
      shouldBeBigDecimal(response.json \ "canPayInThisMonth", BigDecimal("22.12"))
      shouldBeBigDecimal(response.json \ "maximumPaidInThisMonth", BigDecimal(50))
      (response.json \ "thisMonthEndDate").as[String] shouldBe "2018-04-30"
      (response.json \ "nextPaymentMonthStartDate").as[String] shouldBe "2018-05-01"

      (response.json \ "accountHolderName").as[String] shouldBe "Testfore Testsur"
      response.json.as[JsObject].keys should not contain "accountHolderEmail"

      val firstBonusTermJson = (response.json \ "bonusTerms") (0)
      shouldBeBigDecimal(firstBonusTermJson \ "bonusEstimate", BigDecimal("90.99"))
      shouldBeBigDecimal(firstBonusTermJson \ "bonusPaid", BigDecimal("90.99"))
      (firstBonusTermJson \ "endDate").as[String] shouldBe "2019-12-31"
      (firstBonusTermJson \ "bonusPaidOnOrAfterDate").as[String] shouldBe "2020-01-01"

      val secondBonusTermJson = (response.json \ "bonusTerms") (1)
      shouldBeBigDecimal(secondBonusTermJson \ "bonusEstimate", BigDecimal(12))
      shouldBeBigDecimal(secondBonusTermJson \ "bonusPaid", BigDecimal(0))
      (secondBonusTermJson \ "endDate").as[String] shouldBe "2021-12-31"
      (secondBonusTermJson \ "bonusPaidOnOrAfterDate").as[String] shouldBe "2022-01-01"
    }

    "respond with 404 and account not found" in {
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsNotEnrolled()

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino").get())

      response.status shouldBe 404

      (response.json\ "code").as[String] shouldBe "ACCOUNT_NOT_FOUND"
      (response.json\ "message").as[String] shouldBe "No Help to Save account exists for the specified NINO"
    }

    "respond with 500 with general error message body when get account fails" in {
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.accountReturnsInternalServerError(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino").get())

      response.status shouldBe 500

      (response.json\ "code").as[String] shouldBe "GENERAL"
    }

    "respond with 500 with general error message body when get account returns JSON that doesn't conform to the schema" in {
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.accountReturnsInvalidJson(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino").get())

      response.status shouldBe 500

      (response.json\ "code").as[String] shouldBe "GENERAL"
    }

    "include account closure fields when account is closed" in {
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.closedAccountExists(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino").get())
      response.status shouldBe 200

      (response.json \ "number").as[String] shouldBe "1000000000002"
      (response.json \ "openedYearMonth").as[String] shouldBe "2018-03"

      (response.json \ "isClosed").as[Boolean] shouldBe true
      (response.json \ "closureDate").as[String] shouldBe "2018-04-09"
      shouldBeBigDecimal(response.json \ "closingBalance", BigDecimal(10))

      (response.json \ "blocked" \ "unspecified").as[Boolean] shouldBe false
      shouldBeBigDecimal(response.json \ "balance", BigDecimal(0))
      shouldBeBigDecimal(response.json \ "paidInThisMonth", BigDecimal(0))
      shouldBeBigDecimal(response.json \ "canPayInThisMonth", BigDecimal(50))
      shouldBeBigDecimal(response.json \ "maximumPaidInThisMonth", BigDecimal(50))
      (response.json \ "thisMonthEndDate").as[String] shouldBe "2018-04-30"
      (response.json \ "nextPaymentMonthStartDate").as[String] shouldBe "2018-05-01"

      val firstBonusTermJson = (response.json \ "bonusTerms") (0)
      shouldBeBigDecimal(firstBonusTermJson \ "bonusEstimate", BigDecimal("7.50"))
      shouldBeBigDecimal(firstBonusTermJson \ "bonusPaid", BigDecimal(0))
      (firstBonusTermJson \ "endDate").as[String] shouldBe "2020-02-29"
      (firstBonusTermJson \ "bonusPaidOnOrAfterDate").as[String] shouldBe "2020-03-01"

      val secondBonusTermJson = (response.json \ "bonusTerms") (1)
      shouldBeBigDecimal(secondBonusTermJson \ "bonusEstimate", BigDecimal(0))
      shouldBeBigDecimal(secondBonusTermJson \ "bonusPaid", BigDecimal(0))
      (secondBonusTermJson \ "endDate").as[String] shouldBe "2022-02-28"
      (secondBonusTermJson \ "bonusPaidOnOrAfterDate").as[String] shouldBe "2022-03-01"
    }

    "include account blocked fields when account is enrolled but blocked" in {
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.blockedAccountExists(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino").get())
      response.status shouldBe 200

      (response.json \ "number").as[String] shouldBe "1100000112068"
      (response.json \ "openedYearMonth").as[String] shouldBe "2017-11"

      (response.json \ "isClosed").as[Boolean] shouldBe false
      (response.json \ "closureDate").asOpt[String] shouldBe None
      (response.json \ "closingBalance").asOpt[String] shouldBe None

      (response.json \ "blocked" \ "unspecified").as[Boolean] shouldBe true
      shouldBeBigDecimal(response.json \ "balance", BigDecimal(250))
      shouldBeBigDecimal(response.json \ "paidInThisMonth", BigDecimal(50))
      shouldBeBigDecimal(response.json \ "canPayInThisMonth", BigDecimal(0))
      shouldBeBigDecimal(response.json \ "maximumPaidInThisMonth", BigDecimal(50))
      (response.json \ "thisMonthEndDate").as[String] shouldBe "2018-03-31"
      (response.json \ "nextPaymentMonthStartDate").as[String] shouldBe "2018-04-01"

      val firstBonusTermJson = (response.json \ "bonusTerms") (0)
      shouldBeBigDecimal(firstBonusTermJson \ "bonusEstimate", BigDecimal(125.0))
      shouldBeBigDecimal(firstBonusTermJson \ "bonusPaid", BigDecimal(0))
      (firstBonusTermJson \ "endDate").as[String] shouldBe "2019-10-31"
      (firstBonusTermJson \ "bonusPaidOnOrAfterDate").as[String] shouldBe "2019-11-01"

      val secondBonusTermJson = (response.json \ "bonusTerms") (1)
      shouldBeBigDecimal(secondBonusTermJson \ "bonusEstimate", BigDecimal(0))
      shouldBeBigDecimal(secondBonusTermJson \ "bonusPaid", BigDecimal(0))
      (secondBonusTermJson \ "endDate").as[String] shouldBe "2021-10-31"
      (secondBonusTermJson \ "bonusPaidOnOrAfterDate").as[String] shouldBe "2021-11-01"
    }

    "return 401 when the user is not logged in" in {
      AuthStub.userIsNotLoggedIn()
      val response: WSResponse = await(wsUrl(s"/savings-account/$nino").get())
      response.status shouldBe 401
      response.body shouldBe "Authorisation failure [Bearer token not supplied]"
    }

    "return 403 Forbidden when the user is logged in with an insufficient confidence level" in {
      AuthStub.userIsLoggedInWithInsufficientConfidenceLevel()
      val response: WSResponse = await(wsUrl(s"/savings-account/$nino").get())
      response.status shouldBe 403
      response.body shouldBe "Authorisation failure [Insufficient ConfidenceLevel]"
    }
  }
}
