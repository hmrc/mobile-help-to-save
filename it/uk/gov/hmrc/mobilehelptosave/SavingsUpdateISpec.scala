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
import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSResponse
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mobilehelptosave.scalatest.SchemaMatchers
import uk.gov.hmrc.mobilehelptosave.stubs.ShutteringStub.stubForShutteringDisabled
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub}
import uk.gov.hmrc.mobilehelptosave.support.{ComponentSupport, OneServerPerSuiteWsClient, WireMockSupport}

import java.time.{LocalDate, YearMonth}
import java.time.temporal.TemporalAdjusters
import java.util.UUID.randomUUID

class SavingsUpdateISpec
    extends WordSpec
    with Matchers
    with SchemaMatchers
    with TransactionTestData
    with FutureAwaits
    with DefaultAwaitTimeout
    with WireMockSupport
    with OneServerPerSuiteWsClient
    with ComponentSupport {

  override implicit lazy val app: Application = appBuilder.build()

  private val generator = new Generator(0)
  private val nino      = generator.nextNino
  private val journeyId = randomUUID().toString

  "GET /savings-account/savings-update" should {

    "respond with 200 and the users savings update" in {

      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.transactionsExistForUser(nino, dateDynamicTransactionsReturnedByHelpToSaveJsonString)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.accountExists(123.45, nino = nino, openedYearMonth = YearMonth.now().minusMonths(6))

      val response: WSResponse = await(wsUrl(s"/savings-update?journeyId=$journeyId").get())
      response.status shouldBe Status.OK
      (response.json \ "reportStartDate")
        .as[LocalDate] shouldBe LocalDate.now().minusMonths(6).`with`(TemporalAdjusters.firstDayOfMonth())
      (response.json \ "reportEndDate")
        .as[LocalDate]                                                   shouldBe LocalDate.now().minusMonths(1).`with`(TemporalAdjusters.lastDayOfMonth())
      (response.json \ "accountOpenedYearMonth").as[String]              shouldBe YearMonth.now().minusMonths(6).toString
      (response.json \ "savingsUpdate").isDefined                        shouldBe true
      (response.json \ "savingsUpdate" \ "savedInPeriod").as[BigDecimal] shouldBe BigDecimal(2.12)
      (response.json \ "savingsUpdate" \ "monthsSaved").as[Int]          shouldBe 2
      (response.json \ "bonusUpdate").isDefined                          shouldBe true
      (response.json \ "bonusUpdate" \ "currentBonus").as[BigDecimal]    shouldBe BigDecimal(90.99)
    }

    "respond with 200 and savings update section if no transactions are found" in {

      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.zeroTransactionsExistForUser(nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.accountExists(123.45, nino = nino, openedYearMonth = YearMonth.now().minusMonths(6))

      val response: WSResponse = await(wsUrl(s"/savings-update?journeyId=$journeyId").get())
      response.status shouldBe Status.OK
      (response.json \ "reportStartDate")
        .as[LocalDate] shouldBe LocalDate.now().minusMonths(6).`with`(TemporalAdjusters.firstDayOfMonth())
      (response.json \ "reportEndDate")
        .as[LocalDate]                                                shouldBe LocalDate.now().minusMonths(1).`with`(TemporalAdjusters.lastDayOfMonth())
      (response.json \ "accountOpenedYearMonth").as[String]           shouldBe YearMonth.now().minusMonths(6).toString
      (response.json \ "savingsUpdate").isEmpty                       shouldBe true
      (response.json \ "bonusUpdate").isDefined                       shouldBe true
      (response.json \ "bonusUpdate" \ "currentBonus").as[BigDecimal] shouldBe BigDecimal(90.99)
    }

    "respond with a 404 if the user's account isn't found" in {
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.userAccountNotFound(nino)
      stubForShutteringDisabled

      val response: WSResponse = await(wsUrl(s"/savings-update?journeyId=$journeyId").get())

      response.status shouldBe 404
      val jsonBody: JsValue = response.json
      (jsonBody \ "code").as[String]    shouldBe "ACCOUNT_NOT_FOUND"
      (jsonBody \ "message").as[String] shouldBe "No Help to Save account exists for the specified NINO"
    }

    "return 401 when the user is not logged in" in {
      AuthStub.userIsNotLoggedIn()
      val response = await(wsUrl(s"/savings-update?journeyId=$journeyId").get())
      response.status shouldBe 401
      response.body   shouldBe "Authorisation failure [Bearer token not supplied]"
    }

    "return 403 Forbidden when the user is logged in with an insufficient confidence level" in {
      AuthStub.userIsLoggedInWithInsufficientConfidenceLevel()
      val response = await(wsUrl(s"/savings-update?journeyId=$journeyId").get())
      response.status shouldBe 403
      response.body   shouldBe "Authorisation failure [Insufficient ConfidenceLevel]"
    }

    "return 400 when journeyId is not supplied" in {
      AuthStub.userIsNotLoggedIn()
      val response = await(wsUrl(s"/savings-update").get())
      response.status shouldBe 400
    }

    "return 400 when invalid journeyId is supplied" in {
      AuthStub.userIsNotLoggedIn()
      val response = await(wsUrl(s"/savings-update?journeyId=ThisIsAnInvalidJourneyId").get())
      response.status shouldBe 400
    }
  }
}
