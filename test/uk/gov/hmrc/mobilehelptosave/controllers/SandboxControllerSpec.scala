/*
 * Copyright 2022 HM Revenue & Customs
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
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.OneInstancePerTest
import play.api.libs.json.{JsArray, JsValue}
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.SandboxDataConfig
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.sandbox.SandboxData
import uk.gov.hmrc.mobilehelptosave.scalatest.SchemaMatchers
import uk.gov.hmrc.mobilehelptosave.services.FixedFakeClock
import uk.gov.hmrc.mobilehelptosave.support.{LoggerStub, ShutteringMocking}
import uk.gov.hmrc.mobilehelptosave.{AccountTestData, NumberVerification, TransactionTestData}

import java.time.temporal.TemporalAdjusters
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SandboxControllerSpec
    extends AnyWordSpecLike
    with Matchers
    with SchemaMatchers
    with MockFactory
    with OneInstancePerTest
    with LoggerStub
    with FutureAwaits
    with TransactionTestData
    with AccountTestData
    with DefaultAwaitTimeout
    with NumberVerification
    with ShutteringMocking {

  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private val generator   = new Generator(0)
  private val nino        = generator.nextNino
  private val currentTime = LocalDateTime.of(2021, 7, 29, 12, 30)
  private val fixedClock  = new FixedFakeClock(currentTime)

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
    def transactionCount(): Int = (json \ "transactions").as[JsArray].value.length
  }

  "Sandbox getTransactions" should {
    "return the sandbox transaction data" in {
      shutteringDisabled
      val response: Future[Result] =
        controller.getTransactions(nino, "02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())

      status(response) shouldBe OK
      val json: JsValue = contentAsJson(response)

      json transactionCount () shouldBe 11

      var atIndex = 0
      json operation atIndex       shouldBe "credit"
      json amount atIndex          shouldBe BigDecimal(30)
      json transactionDate atIndex shouldBe "2021-07-29"
      json accountingDate atIndex  shouldBe "2021-07-29"
      json balanceAfter atIndex    shouldBe BigDecimal(230)

      atIndex = 1
      json operation atIndex       shouldBe "credit"
      json amount atIndex          shouldBe BigDecimal(20)
      json transactionDate atIndex shouldBe "2021-06-29"
      json accountingDate atIndex  shouldBe "2021-06-29"
      json balanceAfter atIndex    shouldBe BigDecimal(200)

      atIndex = 2
      json operation atIndex       shouldBe "credit"
      json amount atIndex          shouldBe BigDecimal(18.2)
      json transactionDate atIndex shouldBe "2021-06-29"
      json accountingDate atIndex  shouldBe "2021-06-29"
      json balanceAfter atIndex    shouldBe BigDecimal(180)

      atIndex = 3
      json operation atIndex       shouldBe "credit"
      json amount atIndex          shouldBe BigDecimal(10.4)
      json transactionDate atIndex shouldBe "2021-05-29"
      json accountingDate atIndex  shouldBe "2021-05-29"
      json balanceAfter atIndex    shouldBe BigDecimal(161.8)

      atIndex = 4
      json operation atIndex       shouldBe "credit"
      json amount atIndex          shouldBe BigDecimal(35)
      json transactionDate atIndex shouldBe "2021-04-29"
      json accountingDate atIndex  shouldBe "2021-04-29"
      json balanceAfter atIndex    shouldBe BigDecimal(151.4)

      atIndex = 5
      json operation atIndex       shouldBe "credit"
      json amount atIndex          shouldBe BigDecimal(15)
      json transactionDate atIndex shouldBe "2021-04-29"
      json accountingDate atIndex  shouldBe "2021-04-29"
      json balanceAfter atIndex    shouldBe BigDecimal(116.4)

      atIndex = 6
      json operation atIndex       shouldBe "credit"
      json amount atIndex          shouldBe BigDecimal(6)
      json transactionDate atIndex shouldBe "2021-03-29"
      json accountingDate atIndex  shouldBe "2021-03-29"
      json balanceAfter atIndex    shouldBe BigDecimal(101.4)

      atIndex = 7
      json operation atIndex       shouldBe "credit"
      json amount atIndex          shouldBe BigDecimal(20.4)
      json transactionDate atIndex shouldBe "2021-02-28"
      json accountingDate atIndex  shouldBe "2021-02-28"
      json balanceAfter atIndex    shouldBe BigDecimal(95.4)

      atIndex = 8
      json operation atIndex       shouldBe "credit"
      json amount atIndex          shouldBe BigDecimal(10)
      json transactionDate atIndex shouldBe "2021-02-28"
      json accountingDate atIndex  shouldBe "2021-02-28"
      json balanceAfter atIndex    shouldBe BigDecimal(75)

      atIndex = 9
      json operation atIndex       shouldBe "credit"
      json amount atIndex          shouldBe BigDecimal(25)
      json transactionDate atIndex shouldBe "2021-01-29"
      json accountingDate atIndex  shouldBe "2021-01-29"
      json balanceAfter atIndex    shouldBe BigDecimal(65)

      atIndex = 10
      json operation atIndex       shouldBe "credit"
      json amount atIndex          shouldBe BigDecimal(40)
      json transactionDate atIndex shouldBe "2020-12-29"
      json accountingDate atIndex  shouldBe "2020-12-29"
      json balanceAfter atIndex    shouldBe BigDecimal(40)
    }

    "return a shuttered response when the service is shuttered" in {
      shutteringEnabled
      val response: Future[Result] =
        controller.getTransactions(nino, "02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())
      status(response) shouldBe 521
      contentAsJson(response)
        .as[Shuttering] shouldBe shuttered
    }
  }

  "Sandbox getAccount" should {
    "return the sandbox account data" in {
      shutteringDisabled
      val response: Future[Result] = controller.getAccount(nino, "02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())

      status(response) shouldBe OK
      val json: JsValue = contentAsJson(response)

      (json \ "number").as[String]                     shouldBe "1100000112057"
      (json \ "openedYearMonth").as[String]            shouldBe "2020-02"
      (json \ "isClosed").as[Boolean]                  shouldBe false
      (json \ "balance").as[BigDecimal]                shouldBe BigDecimal(100)
      (json \ "paidInThisMonth").as[BigDecimal]        shouldBe BigDecimal(30.0)
      (json \ "canPayInThisMonth").as[BigDecimal]      shouldBe BigDecimal(20.0)
      (json \ "maximumPaidInThisMonth").as[BigDecimal] shouldBe BigDecimal(50)
      (json \ "thisMonthEndDate").as[String]           shouldBe "2021-07-31"

      val firstBonusTermJson = (json \ "bonusTerms")(0)
      shouldBeBigDecimal(firstBonusTermJson \ "bonusEstimate", BigDecimal("75"))
      shouldBeBigDecimal(firstBonusTermJson \ "bonusPaid", BigDecimal("0"))
      (firstBonusTermJson \ "endDate").as[String]                shouldBe "2022-01-31"
      (firstBonusTermJson \ "bonusPaidOnOrAfterDate").as[String] shouldBe "2022-02-01"
      (firstBonusTermJson \ "bonusPaidByDate").as[String]        shouldBe "2022-02-01"

      val secondBonusTermJson = (json \ "bonusTerms")(1)
      shouldBeBigDecimal(secondBonusTermJson \ "bonusEstimate", BigDecimal(0))
      shouldBeBigDecimal(secondBonusTermJson \ "bonusPaid", BigDecimal(0))
      (secondBonusTermJson \ "endDate").as[String]                    shouldBe "2024-01-31"
      (secondBonusTermJson \ "bonusPaidOnOrAfterDate").as[String]     shouldBe "2024-02-01"
      (secondBonusTermJson \ "bonusPaidByDate").as[String]            shouldBe "2024-02-01"

      (json \ "inAppPaymentsEnabled").as[Boolean] shouldBe false
    }

    "return a shuttered response when the service is shuttered" in {
      shutteringEnabled
      val response: Future[Result] = controller.getAccount(nino, "02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())
      status(response) shouldBe 521
      contentAsJson(response)
        .as[Shuttering] shouldBe shuttered
    }
  }

  "Sandbox getMilestones" should {
    "return the sandbox milestones data" in {
      shutteringDisabled
      val response: Future[Result] =
        controller.getMilestones(nino, "02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())

      status(response) shouldBe OK
      val json: JsValue = contentAsJson(response)

      val milestone = (json \ "milestones").isEmpty
    }

    "return a shuttered response when the service is shuttered" in {
      shutteringEnabled
      val response: Future[Result] =
        controller.getMilestones(nino, "02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())
      status(response) shouldBe 521
      contentAsJson(response)
        .as[Shuttering] shouldBe shuttered
    }
  }

  "Sandbox getSavingsUpdate" should {
    "return the sandbox savings update data" in {
      shutteringDisabled
      val savingsUpdate: Future[Result] =
        controller.getSavingsUpdate("02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())

      status(savingsUpdate) shouldBe OK
      val jsonBody = contentAsJson(savingsUpdate)
      (jsonBody \ "reportStartDate")
        .as[LocalDate] shouldBe currentTime.minusMonths(8).`with`(TemporalAdjusters.firstDayOfMonth()).toLocalDate
      (jsonBody \ "reportEndDate")
        .as[LocalDate]                                                               shouldBe currentTime.minusMonths(1).`with`(TemporalAdjusters.lastDayOfMonth()).toLocalDate
      (jsonBody \ "accountOpenedYearMonth").as[String]                               shouldBe YearMonth.from(currentTime).minusMonths(17).toString
      (jsonBody \ "savingsUpdate").isDefined                                         shouldBe true
      (jsonBody \ "savingsUpdate" \ "savedInPeriod").as[BigDecimal]                  shouldBe 100
      (jsonBody \ "savingsUpdate" \ "savedByMonth").isDefined                        shouldBe true
      (jsonBody \ "savingsUpdate" \ "savedByMonth" \ "monthsSaved").as[Int]          shouldBe 4
      (jsonBody \ "savingsUpdate" \ "savedByMonth" \ "numberOfMonths").as[Int]       shouldBe 5
      (jsonBody \ "savingsUpdate" \ "goalsReached").isDefined                        shouldBe true
      (jsonBody \ "savingsUpdate" \ "goalsReached" \ "currentAmount").as[Double]     shouldBe 25.0
      (jsonBody \ "savingsUpdate" \ "goalsReached" \ "numberOfTimesReached").as[Int] shouldBe 2
      (jsonBody \ "savingsUpdate" \ "goalsReached" \ "currentGoalName").as[String]   shouldBe "\uD83C\uDFE1 New home"
      (jsonBody \ "savingsUpdate" \ "amountEarnedTowardsBonus").as[BigDecimal]       shouldBe 75.00
      (jsonBody \ "bonusUpdate").isDefined                                           shouldBe true
      (jsonBody \ "bonusUpdate" \ "currentBonusTerm").as[String]                     shouldBe "First"
      (jsonBody \ "bonusUpdate" \ "monthsUntilBonus").as[Int]                        shouldBe 8
      (jsonBody \ "bonusUpdate" \ "currentBonus").as[BigDecimal]                     shouldBe 50
      (jsonBody \ "bonusUpdate" \ "highestBalance").isEmpty                          shouldBe true
      (jsonBody \ "bonusUpdate" \ "potentialBonusAtCurrentRate").as[BigDecimal]      shouldBe 300
      (jsonBody \ "bonusUpdate" \ "potentialBonusWithFiveMore").as[BigDecimal]       shouldBe 500
      (jsonBody \ "bonusUpdate" \ "maxBonus").as[BigDecimal]                         shouldBe 600
    }

    "return a shuttered response when the service is shuttered" in {
      shutteringEnabled
      val response: Future[Result] =
        controller.getSavingsUpdate("02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())
      status(response) shouldBe 521
      contentAsJson(response)
        .as[Shuttering] shouldBe shuttered
    }
  }

}

object TestSandboxDataConfig extends SandboxDataConfig {
  override val inAppPaymentsEnabled: Boolean = false
}
