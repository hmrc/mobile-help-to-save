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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.Application
import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{WSRequest, WSResponse}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mobilehelptosave.domain.TestSavingsGoal
import uk.gov.hmrc.mobilehelptosave.stubs.ShutteringStub.stubForShutteringDisabled
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub}
import uk.gov.hmrc.mobilehelptosave.support.{ComponentSupport, OneServerPerSuiteWsClient, WireMockSupport}

import java.time.{LocalDate, YearMonth}
import java.time.temporal.TemporalAdjusters

class SavingsUpdateISpec
    extends AnyWordSpecLike
    with Matchers
    with TransactionTestData
    with FutureAwaits
    with DefaultAwaitTimeout
    with WireMockSupport
    with OneServerPerSuiteWsClient
    with ComponentSupport {

  override implicit lazy val app: Application = appBuilder.build()
  val clearGoalEventsUrl           = "/mobile-help-to-save/test-only/clear-goal-events"
  val createGoalUrl                = "/mobile-help-to-save/test-only/create-goal"
  private val generator            = new Generator(0)
  private val nino                 = generator.nextNino
  private val journeyId            = "27085215-69a4-4027-8f72-b04b10ec16b0"
  private val applicationRouterKey = "application.router"
  private val testOnlyRoutes       = "testOnlyDoNotUseInAppConf.Routes"

  private val acceptJsonHeader:        (String, String) = "Accept"        -> "application/vnd.hmrc.1.0+json"
  private val authorisationJsonHeader: (String, String) = "AUTHORIZATION" -> "Bearer 123"

  private def requestWithAuthHeaders(url: String): WSRequest =
    wsUrl(url).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)

  System.setProperty(applicationRouterKey, testOnlyRoutes)

  s"GET $clearGoalEventsUrl with $applicationRouterKey set to $testOnlyRoutes" should {
    s"Return 200 " in (await(requestWithAuthHeaders(clearGoalEventsUrl).get).status shouldBe 200)
  }

  "GET /savings-account/savings-update" should {

    "respond with 200 and the users savings update" in {

      stubForShutteringDisabled()
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.transactionsExistForUser(nino, dateDynamicTransactionsReturnedByHelpToSaveJsonString)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.savingsUpdateAccountExists(123.45, nino = nino)

      await(
        wsUrl(createGoalUrl)
          .put(Json.toJson(TestSavingsGoal(nino, Some(10.0), None, LocalDate.now().minusMonths(8))))
      ).status shouldBe 201

      await(
        wsUrl(createGoalUrl)
          .put(Json.toJson(TestSavingsGoal(nino, Some(30.0), Some("My Goal"), LocalDate.now().minusMonths(3))))
      ).status shouldBe 201

      val response: WSResponse = await(requestWithAuthHeaders(s"/savings-update?journeyId=$journeyId").get())
      response.status shouldBe Status.OK
      (response.json \ "reportStartDate")
        .as[LocalDate] shouldBe LocalDate.now().minusMonths(6).`with`(TemporalAdjusters.firstDayOfMonth())
      (response.json \ "reportEndDate")
        .as[LocalDate]                                                                    shouldBe LocalDate.now().minusMonths(1).`with`(TemporalAdjusters.lastDayOfMonth())
      (response.json \ "accountOpenedYearMonth").as[String]                               shouldBe YearMonth.now().minusMonths(6).toString
      (response.json \ "savingsUpdate").isDefined                                         shouldBe true
      (response.json \ "savingsUpdate" \ "savedInPeriod").as[BigDecimal]                  shouldBe 87.61
      (response.json \ "savingsUpdate" \ "savedByMonth").isDefined                        shouldBe true
      (response.json \ "savingsUpdate" \ "savedByMonth" \ "monthsSaved").as[Int]          shouldBe 4
      (response.json \ "savingsUpdate" \ "savedByMonth" \ "numberOfMonths").as[Int]       shouldBe 6
      (response.json \ "savingsUpdate" \ "goalsReached").isDefined                        shouldBe true
      (response.json \ "savingsUpdate" \ "goalsReached" \ "currentAmount").as[Double]     shouldBe 30.0
      (response.json \ "savingsUpdate" \ "goalsReached" \ "currentGoalName").as[String]   shouldBe "My Goal"
      (response.json \ "savingsUpdate" \ "goalsReached" \ "numberOfTimesReached").as[Int] shouldBe 2
      (response.json \ "savingsUpdate" \ "amountEarnedTowardsBonus").as[BigDecimal]       shouldBe 24.5
      (response.json \ "bonusUpdate").isDefined                                           shouldBe true
      (response.json \ "bonusUpdate" \ "currentBonusTerm").as[String]                     shouldBe "First"
      (response.json \ "bonusUpdate" \ "monthsUntilBonus").as[Int]                        shouldBe 19
      (response.json \ "bonusUpdate" \ "currentBonus").as[BigDecimal]                     shouldBe 90.99
      (response.json \ "bonusUpdate" \ "highestBalance").as[BigDecimal]                   shouldBe 181.98
      (response.json \ "bonusUpdate" \ "potentialBonusAtCurrentRate").as[BigDecimal]      shouldBe 193.14
      (response.json \ "bonusUpdate" \ "potentialBonusWithFiveMore").as[BigDecimal]       shouldBe 238.14
      (response.json \ "bonusUpdate" \ "maxBonus").as[BigDecimal]                         shouldBe 522.79

      await(requestWithAuthHeaders(clearGoalEventsUrl).get).status shouldBe 200
    }

    "respond with 200 and no savings update section if no transactions are found" in {

      stubForShutteringDisabled()
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.zeroTransactionsExistForUser(nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.accountExists(123.45, nino = nino, openedYearMonth = YearMonth.now().minusMonths(6))

      val response: WSResponse = await(requestWithAuthHeaders(s"/savings-update?journeyId=$journeyId").get())
      response.status shouldBe Status.OK
      (response.json \ "reportStartDate")
        .as[LocalDate] shouldBe LocalDate.now().minusMonths(6).`with`(TemporalAdjusters.firstDayOfMonth())
      (response.json \ "reportEndDate")
        .as[LocalDate]                                      shouldBe LocalDate.now().minusMonths(1).`with`(TemporalAdjusters.lastDayOfMonth())
      (response.json \ "accountOpenedYearMonth").as[String] shouldBe YearMonth.now().minusMonths(6).toString
      (response.json \ "savingsUpdate").isEmpty             shouldBe true
      (response.json \ "bonusUpdate").isDefined             shouldBe true
    }

    "respond with a 404 if the user's account isn't found" in {

      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.userAccountNotFound(nino)
      stubForShutteringDisabled

      val response: WSResponse = await(requestWithAuthHeaders(s"/savings-update?journeyId=$journeyId").get())

      response.status shouldBe 404
      val jsonBody: JsValue = response.json
      (jsonBody \ "code").as[String]    shouldBe "ACCOUNT_NOT_FOUND"
      (jsonBody \ "message").as[String] shouldBe "No Help to Save account exists for the specified NINO"
    }

    "return 401 when the user is not logged in" in {
      stubForShutteringDisabled()
      AuthStub.userIsNotLoggedIn()
      val response = await(requestWithAuthHeaders(s"/savings-update?journeyId=$journeyId").get())
      response.status shouldBe 401
      response.body   shouldBe "Authorisation failure [Bearer token not supplied]"
    }

    "return 403 Forbidden when the user is logged in with an insufficient confidence level" in {
      stubForShutteringDisabled()
      AuthStub.userIsLoggedInWithInsufficientConfidenceLevel()
      val response = await(requestWithAuthHeaders(s"/savings-update?journeyId=$journeyId").get())
      response.status shouldBe 403
      response.body   shouldBe "Authorisation failure [Insufficient ConfidenceLevel]"
    }

    "return 400 when journeyId is not supplied" in {
      AuthStub.userIsNotLoggedIn()
      val response = await(requestWithAuthHeaders(s"/savings-update").get())
      response.status shouldBe 400
    }

    "return 400 when invalid journeyId is supplied" in {
      AuthStub.userIsNotLoggedIn()
      val response = await(requestWithAuthHeaders(s"/savings-update?journeyId=ThisIsAnInvalidJourneyId").get())
      response.status shouldBe 400
    }
  }
}
