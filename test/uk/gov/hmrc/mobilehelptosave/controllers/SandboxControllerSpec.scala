/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.mobilehelptosave.controllers

import java.time.{LocalDate, LocalDateTime, YearMonth}
import eu.timepit.refined.auto._
import play.api.LoggerLike
import play.api.libs.json.{JsArray, JsValue}
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.FakeRequest
import uk.gov.hmrc.mobilehelptosave.config.SandboxDataConfig
import uk.gov.hmrc.mobilehelptosave.connectors.HttpClientV2Helper
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.sandbox.SandboxData
import uk.gov.hmrc.mobilehelptosave.services.FixedFakeClock
import uk.gov.hmrc.mobilehelptosave.support.{BaseSpec, ShutteringMocking}
import uk.gov.hmrc.mobilehelptosave.{AccountTestData, NumberVerification, TransactionTestData}

import java.time.temporal.TemporalAdjusters
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SandboxControllerSpec
    extends HttpClientV2Helper
    with TransactionTestData
    with AccountTestData
    with NumberVerification
    with ShutteringMocking {

  private val currentTime = LocalDateTime.of(2022, 4, 11, 12, 30)
  private val fixedClock  = new FixedFakeClock(currentTime)
  private val logger = mock[LoggerLike]

  private val controller: SandboxController =
    new SandboxController(logger,
                          shutteringConnector,
                          SandboxData(logger, fixedClock, TestSandboxDataConfig),
                          stubControllerComponents())
  private val shuttered = Shuttering(shuttered = true, Some("Shuttered"), Some("HTS is currently not available"))

  implicit class TransactionJson(json: JsValue) {
    def operation(transactionIndex: Int): String = ((json \ "transactions")(transactionIndex) \ "operation").as[String]

    def amount(transactionIndex: Int): BigDecimal =
      ((json \ "transactions")(transactionIndex) \ "amount").as[BigDecimal]

    def transactionDate(transactionIndex: Int): String =
      ((json \ "transactions")(transactionIndex) \ "transactionDate").as[String]

    def accountingDate(transactionIndex: Int): String =
      ((json \ "transactions")(transactionIndex) \ "accountingDate").as[String]

    def balanceAfter(transactionIndex: Int): BigDecimal =
      ((json \ "transactions")(transactionIndex) \ "balanceAfter").as[BigDecimal]
  }

  "Sandbox getTransactions" should {
    "return the sandbox transaction data" in {
      shutteringDisabled
      val response: Future[Result] =
        controller.getTransactions(nino, "02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())

      status(response) mustBe OK
      val json: JsValue = contentAsJson(response)

      (json \ "transactions").as[JsArray].value.length mustBe 18

      var atIndex = 0
      json operation atIndex       mustBe "credit"
      json amount atIndex          mustBe BigDecimal(30)
      json transactionDate atIndex mustBe "2022-04-11"
      json accountingDate atIndex  mustBe "2022-04-11"
      json balanceAfter atIndex    mustBe BigDecimal(130)

      atIndex = 1
      json operation atIndex       mustBe "credit"
      json amount atIndex          mustBe BigDecimal(50)
      json transactionDate atIndex mustBe "2022-03-11"
      json accountingDate atIndex  mustBe "2022-03-11"
      json balanceAfter atIndex    mustBe BigDecimal(100)

      atIndex = 2
      json operation atIndex       mustBe "credit"
      json amount atIndex          mustBe BigDecimal(-25)
      json transactionDate atIndex mustBe "2022-02-11"
      json accountingDate atIndex  mustBe "2022-02-11"
      json balanceAfter atIndex    mustBe BigDecimal(50)

      atIndex = 3
      json operation atIndex       mustBe "credit"
      json amount atIndex          mustBe BigDecimal(25)
      json transactionDate atIndex mustBe "2022-02-11"
      json accountingDate atIndex  mustBe "2022-02-11"
      json balanceAfter atIndex    mustBe BigDecimal(75)

      atIndex = 4
      json operation atIndex       mustBe "credit"
      json amount atIndex          mustBe BigDecimal(50)
      json transactionDate atIndex mustBe "2022-01-11"
      json accountingDate atIndex  mustBe "2022-01-11"
      json balanceAfter atIndex    mustBe BigDecimal(50)

      atIndex = 5
      json operation atIndex       mustBe "credit"
      json amount atIndex          mustBe BigDecimal(-25)
      json transactionDate atIndex mustBe "2021-12-11"
      json accountingDate atIndex  mustBe "2021-12-11"
      json balanceAfter atIndex    mustBe BigDecimal(0)

      atIndex = 6
      json operation atIndex       mustBe "credit"
      json amount atIndex          mustBe BigDecimal(25)
      json transactionDate atIndex mustBe "2021-12-11"
      json accountingDate atIndex  mustBe "2021-12-11"
      json balanceAfter atIndex    mustBe BigDecimal(25)

      atIndex = 7
      json operation atIndex       mustBe "credit"
      json amount atIndex          mustBe BigDecimal(-250)
      json transactionDate atIndex mustBe "2021-11-11"
      json accountingDate atIndex  mustBe "2021-11-11"
      json balanceAfter atIndex    mustBe BigDecimal(0)

      atIndex = 8
      json operation atIndex       mustBe "credit"
      json amount atIndex          mustBe BigDecimal(25)
      json transactionDate atIndex mustBe "2021-10-11"
      json accountingDate atIndex  mustBe "2021-10-11"
      json balanceAfter atIndex    mustBe BigDecimal(250)

      atIndex = 9
      json operation atIndex       mustBe "credit"
      json amount atIndex          mustBe BigDecimal(25)
      json transactionDate atIndex mustBe "2021-09-11"
      json accountingDate atIndex  mustBe "2021-09-11"
      json balanceAfter atIndex    mustBe BigDecimal(225)

      atIndex = 10
      json operation atIndex       mustBe "credit"
      json amount atIndex          mustBe BigDecimal(25)
      json transactionDate atIndex mustBe "2021-08-11"
      json accountingDate atIndex  mustBe "2021-08-11"
      json balanceAfter atIndex    mustBe BigDecimal(200)

      atIndex = 11
      json operation atIndex       mustBe "credit"
      json amount atIndex          mustBe BigDecimal(25)
      json transactionDate atIndex mustBe "2021-07-11"
      json accountingDate atIndex  mustBe "2021-07-11"
      json balanceAfter atIndex    mustBe BigDecimal(175)

      atIndex = 12
      json operation atIndex       mustBe "credit"
      json amount atIndex          mustBe BigDecimal(25)
      json transactionDate atIndex mustBe "2021-06-11"
      json accountingDate atIndex  mustBe "2021-06-11"
      json balanceAfter atIndex    mustBe BigDecimal(150)

      atIndex = 13
      json operation atIndex       mustBe "credit"
      json amount atIndex          mustBe BigDecimal(25)
      json transactionDate atIndex mustBe "2021-05-11"
      json accountingDate atIndex  mustBe "2021-05-11"
      json balanceAfter atIndex    mustBe BigDecimal(125)

      atIndex = 14
      json operation atIndex       mustBe "credit"
      json amount atIndex          mustBe BigDecimal(25)
      json transactionDate atIndex mustBe "2021-04-11"
      json accountingDate atIndex  mustBe "2021-04-11"
      json balanceAfter atIndex    mustBe BigDecimal(100)

      atIndex = 15
      json operation atIndex       mustBe "credit"
      json amount atIndex          mustBe BigDecimal(25)
      json transactionDate atIndex mustBe "2021-03-11"
      json accountingDate atIndex  mustBe "2021-03-11"
      json balanceAfter atIndex    mustBe BigDecimal(75)

      atIndex = 16
      json operation atIndex       mustBe "credit"
      json amount atIndex          mustBe BigDecimal(25)
      json transactionDate atIndex mustBe "2021-02-11"
      json accountingDate atIndex  mustBe "2021-02-11"
      json balanceAfter atIndex    mustBe BigDecimal(50)

      atIndex = 17
      json operation atIndex       mustBe "credit"
      json amount atIndex          mustBe BigDecimal(25)
      json transactionDate atIndex mustBe "2021-01-11"
      json accountingDate atIndex  mustBe "2021-01-11"
      json balanceAfter atIndex    mustBe BigDecimal(25)
    }

    "return a shuttered response when the service is shuttered" in {
      shutteringEnabled
      val response: Future[Result] =
        controller.getTransactions(nino, "02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())
      status(response) mustBe 521
      contentAsJson(response)
        .as[Shuttering] mustBe shuttered
    }
  }

  "Sandbox getAccount" should {
    "return the sandbox account data" in {
      shutteringDisabled
      val response: Future[Result] = controller.getAccount(nino, "02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())

      status(response) mustBe OK
      val json: JsValue = contentAsJson(response)

      (json \ "number").as[String]                     mustBe "1100000112057"
      (json \ "openedYearMonth").as[String]            mustBe "2020-11"
      (json \ "isClosed").as[Boolean]                  mustBe false
      (json \ "balance").as[BigDecimal]                mustBe BigDecimal(100)
      (json \ "paidInThisMonth").as[BigDecimal]        mustBe BigDecimal(30.0)
      (json \ "canPayInThisMonth").as[BigDecimal]      mustBe BigDecimal(20.0)
      (json \ "maximumPaidInThisMonth").as[BigDecimal] mustBe BigDecimal(50)
      (json \ "thisMonthEndDate").as[String]           mustBe "2022-04-30"

      val firstBonusTermJson = (json \ "bonusTerms")(0)
      shouldBeBigDecimal(firstBonusTermJson \ "bonusEstimate", BigDecimal("125"))
      shouldBeBigDecimal(firstBonusTermJson \ "bonusPaid", BigDecimal("0"))
      (firstBonusTermJson \ "endDate").as[String]                mustBe "2022-10-31"
      (firstBonusTermJson \ "bonusPaidOnOrAfterDate").as[String] mustBe "2022-11-01"
      (firstBonusTermJson \ "bonusPaidByDate").as[String]        mustBe "2022-11-01"

      val secondBonusTermJson = (json \ "bonusTerms")(1)
      shouldBeBigDecimal(secondBonusTermJson \ "bonusPaid", BigDecimal(0))
      (secondBonusTermJson \ "endDate").as[String]                mustBe "2024-10-31"
      (secondBonusTermJson \ "bonusPaidOnOrAfterDate").as[String] mustBe "2024-11-01"
      (secondBonusTermJson \ "bonusPaidByDate").as[String]        mustBe "2024-11-01"

      (json \ "inAppPaymentsEnabled").as[Boolean] mustBe false
    }

    "return a shuttered response when the service is shuttered" in {
      shutteringEnabled
      val response: Future[Result] = controller.getAccount(nino, "02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())
      status(response) mustBe 521
      contentAsJson(response)
        .as[Shuttering] mustBe shuttered
    }
  }

  "Sandbox getMilestones" should {
    "return the sandbox milestones data" in {
      shutteringDisabled
      val response: Future[Result] =
        controller.getMilestones(nino, "02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())

      status(response) mustBe OK
      val json: JsValue = contentAsJson(response)

      (json \ "milestones").isEmpty mustBe false
    }

    "return a shuttered response when the service is shuttered" in {
      shutteringEnabled
      val response: Future[Result] =
        controller.getMilestones(nino, "02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())
      status(response) mustBe 521
      contentAsJson(response)
        .as[Shuttering] mustBe shuttered
    }
  }

  "Sandbox getSavingsUpdate" should {
    "return the sandbox savings update data" in {
      shutteringDisabled
      val savingsUpdate: Future[Result] =
        controller.getSavingsUpdate("02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())

      status(savingsUpdate) mustBe OK
      val jsonBody = contentAsJson(savingsUpdate)
      (jsonBody \ "reportStartDate")
        .as[LocalDate] mustBe currentTime.minusMonths(5).`with`(TemporalAdjusters.firstDayOfMonth()).toLocalDate
      (jsonBody \ "reportEndDate")
        .as[LocalDate]                                                               mustBe currentTime.minusMonths(1).`with`(TemporalAdjusters.lastDayOfMonth()).toLocalDate
      (jsonBody \ "accountOpenedYearMonth").as[String]                               mustBe YearMonth.from(currentTime).minusMonths(17).toString
      (jsonBody \ "savingsUpdate").isDefined                                         mustBe true
      (jsonBody \ "savingsUpdate" \ "savedInPeriod").as[BigDecimal]                  mustBe 100
      (jsonBody \ "savingsUpdate" \ "savedByMonth").isDefined                        mustBe true
      (jsonBody \ "savingsUpdate" \ "savedByMonth" \ "monthsSaved").as[Int]          mustBe 4
      (jsonBody \ "savingsUpdate" \ "savedByMonth" \ "numberOfMonths").as[Int]       mustBe 5
      (jsonBody \ "savingsUpdate" \ "goalsReached").isDefined                        mustBe true
      (jsonBody \ "savingsUpdate" \ "goalsReached" \ "currentAmount").as[Double]     mustBe 50.0
      (jsonBody \ "savingsUpdate" \ "goalsReached" \ "numberOfTimesReached").as[Int] mustBe 2
      (jsonBody \ "savingsUpdate" \ "goalsReached" \ "currentGoalName").as[String]   mustBe "\uD83C\uDFE1 New home"
      (jsonBody \ "savingsUpdate" \ "amountEarnedTowardsBonus").as[BigDecimal]       mustBe 50.00
      (jsonBody \ "bonusUpdate").isDefined                                           mustBe true
      (jsonBody \ "bonusUpdate" \ "currentBonusTerm").as[String]                     mustBe "First"
      (jsonBody \ "bonusUpdate" \ "monthsUntilBonus").as[Int]                        mustBe 8
      (jsonBody \ "bonusUpdate" \ "currentBonus").as[BigDecimal]                     mustBe 125
      (jsonBody \ "bonusUpdate" \ "highestBalance").as[BigDecimal]                   mustBe 250
      (jsonBody \ "bonusUpdate" \ "potentialBonusAtCurrentRate").as[BigDecimal]      mustBe 125
      (jsonBody \ "bonusUpdate" \ "potentialBonusWithFiveMore").as[BigDecimal]       mustBe 140
      (jsonBody \ "bonusUpdate" \ "maxBonus").as[BigDecimal]                         mustBe 225
    }

    "return a shuttered response when the service is shuttered" in {
      shutteringEnabled
      val response: Future[Result] =
        controller.getSavingsUpdate("02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())
      status(response) mustBe 521
      contentAsJson(response)
        .as[Shuttering] mustBe shuttered
    }
  }

}

object TestSandboxDataConfig extends SandboxDataConfig {
  override val inAppPaymentsEnabled: Boolean = false
}
