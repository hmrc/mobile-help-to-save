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

package uk.gov.hmrc.mobilehelptosave.controllers

import org.joda.time.{DateTime, DateTimeZone}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OneInstancePerTest, WordSpec}
import play.api.libs.json.{JsLookupResult, JsValue}
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.sandbox.SandboxData
import uk.gov.hmrc.mobilehelptosave.scalatest.SchemaMatchers
import uk.gov.hmrc.mobilehelptosave.services.FixedFakeClock
import uk.gov.hmrc.mobilehelptosave.support.LoggerStub
import uk.gov.hmrc.mobilehelptosave.{AccountTestData, NumberVerification, TransactionTestData}

import scala.concurrent.Future

class SandboxControllerSpec
  extends WordSpec
    with Matchers
    with SchemaMatchers
    with MockFactory
    with OneInstancePerTest
    with LoggerStub
    with FutureAwaits
    with TransactionTestData
    with AccountTestData
    with DefaultAwaitTimeout
    with NumberVerification {

  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private val generator = new Generator(0)
  private val nino = generator.nextNino
  private val shuttering = Shuttering(shuttered = false, "", "")
  private val shutteredShuttering = Shuttering(shuttered = true, "Gad Dangit!", "This service is shuttered")
  private val config = TestHelpToSaveControllerConfig(shuttering)
  private val currentTime = new DateTime(2018, 9, 29, 12, 30, DateTimeZone.forID("Europe/London"))
  private val today = currentTime.toLocalDate
  private val fixedClock = new FixedFakeClock(currentTime)
  private val controller: SandboxController = new SandboxController(logger, config, SandboxData(fixedClock))
  private val shutteredController: SandboxController = new SandboxController(logger, config.copy(shuttering = shutteredShuttering), SandboxData(fixedClock))

  implicit class TransactionJson(json: JsValue) {
    def operation(transactionIndex: Int): String = ((json \ "transactions") (transactionIndex) \ "operation").as[String]
    def amount(transactionIndex: Int): BigDecimal = ((json \ "transactions") (transactionIndex) \ "amount").as[BigDecimal]
    def transactionDate(transactionIndex: Int): String = ((json \ "transactions") (transactionIndex) \ "transactionDate").as[String]
    def accountingDate(transactionIndex: Int): String = ((json \ "transactions") (transactionIndex) \ "accountingDate").as[String]
    def balanceAfter(transactionIndex: Int): BigDecimal = ((json \ "transactions") (transactionIndex) \ "balanceAfter").as[BigDecimal]
  }

  "Sandbox getTransactions" should {
    "return the sandbox transaction data" in {

      val response: Future[Result] = controller.getTransactions(nino.value)(FakeRequest())

      status(response) shouldBe OK
      val json: JsValue = contentAsJson(response)

      json operation 0 shouldBe "credit"
      json amount 0 shouldBe BigDecimal(20)
      json transactionDate 0 shouldBe "2018-08-29"
      json accountingDate 0 shouldBe "2018-08-29"
      json balanceAfter 0 shouldBe BigDecimal(200)

      json operation 1 shouldBe "credit"
      json amount 1 shouldBe BigDecimal(18.2)
      json transactionDate 1 shouldBe "2018-08-29"
      json accountingDate 1 shouldBe "2018-08-29"
      json balanceAfter 1 shouldBe BigDecimal(180)

      json operation 2 shouldBe "credit"
      json amount 2 shouldBe BigDecimal(10.4)
      json transactionDate 2 shouldBe "2018-07-29"
      json accountingDate 2 shouldBe "2018-07-29"
      json balanceAfter 2 shouldBe BigDecimal(161.8)

      json operation 3 shouldBe "credit"
      json amount 3 shouldBe BigDecimal(35)
      json transactionDate 3 shouldBe "2018-06-29"
      json accountingDate 3 shouldBe "2018-06-29"
      json balanceAfter 3 shouldBe BigDecimal(151.4)

      json operation 4 shouldBe "credit"
      json amount 4 shouldBe BigDecimal(15)
      json transactionDate 4 shouldBe "2018-06-29"
      json accountingDate 4 shouldBe "2018-06-29"
      json balanceAfter 4 shouldBe BigDecimal(116.4)

      json operation 5 shouldBe "credit"
      json amount 5 shouldBe BigDecimal(6)
      json transactionDate 5 shouldBe "2018-05-29"
      json accountingDate 5 shouldBe "2018-05-29"
      json balanceAfter 5 shouldBe BigDecimal(101.4)

      json operation 6 shouldBe "credit"
      json amount 6 shouldBe BigDecimal(20.4)
      json transactionDate 6 shouldBe "2018-04-29"
      json accountingDate 6 shouldBe "2018-04-29"
      json balanceAfter 6 shouldBe BigDecimal(95.4)

      json operation 7 shouldBe "credit"
      json amount 7 shouldBe BigDecimal(10)
      json transactionDate 7 shouldBe "2018-04-29"
      json accountingDate 7 shouldBe "2018-04-29"
      json balanceAfter 7 shouldBe BigDecimal(75)

      json operation 8 shouldBe "credit"
      json amount 8 shouldBe BigDecimal(25)
      json transactionDate 8 shouldBe "2018-03-29"
      json accountingDate 8 shouldBe "2018-03-29"
      json balanceAfter 8 shouldBe BigDecimal(65)

      json operation 9 shouldBe "credit"
      json amount 9 shouldBe BigDecimal(40)
      json transactionDate 9 shouldBe "2018-02-28"
      json accountingDate 9 shouldBe "2018-02-28"
      json balanceAfter 9 shouldBe BigDecimal(40)
    }

    "return a shuttered response when the service is shuttered" in {

      val response: Future[Result] = shutteredController.getTransactions(nino.value)(FakeRequest())
      status(response) shouldBe 521
      contentAsJson(response).as[Shuttering] shouldBe Shuttering(shuttered = true, "Gad Dangit!", "This service is shuttered")
    }
  }

  "Sandbox getAccount" should {
    "return the sandbox account data" in {

      val response: Future[Result] = controller.getAccount(nino.value)(FakeRequest())

      status(response) shouldBe OK
      val json: JsValue = contentAsJson(response)

      (json \ "number").as[String] shouldBe "1100000112057"
      (json \ "openedYearMonth").as[String] shouldBe "2018-02"
      (json \ "isClosed").as[Boolean] shouldBe false
      (json \ "blocked" \ "unspecified").as[Boolean] shouldBe false
      (json \ "balance").as[BigDecimal] shouldBe BigDecimal(220.5)
      (json \ "paidInThisMonth").as[BigDecimal] shouldBe BigDecimal(20.5)
      (json \ "canPayInThisMonth").as[BigDecimal] shouldBe BigDecimal(29.5)
      (json \ "maximumPaidInThisMonth").as[BigDecimal] shouldBe BigDecimal(50)
      (json \ "thisMonthEndDate").as[String] shouldBe "2018-09-30"

      val firstBonusTermJson: JsLookupResult = (json \ "bonusTerms") (0)
      shouldBeBigDecimal(firstBonusTermJson \ "bonusEstimate", BigDecimal("110.25"))
      shouldBeBigDecimal(firstBonusTermJson \ "bonusPaid", BigDecimal("0"))
      (firstBonusTermJson \ "endDate").as[String] shouldBe "2020-01-31"
      (firstBonusTermJson \ "bonusPaidOnOrAfterDate").as[String] shouldBe "2020-02-01"

      val secondBonusTermJson: JsLookupResult = (json \ "bonusTerms") (1)
      shouldBeBigDecimal(secondBonusTermJson \ "bonusEstimate", BigDecimal(0))
      shouldBeBigDecimal(secondBonusTermJson \ "bonusPaid", BigDecimal(0))
      (secondBonusTermJson \ "endDate").as[String] shouldBe "2022-01-31"
      (secondBonusTermJson \ "bonusPaidOnOrAfterDate").as[String] shouldBe "2022-02-01"
    }

    "return a shuttered response when the service is shuttered" in {

      val response: Future[Result] = shutteredController.getAccount(nino.value)(FakeRequest())
      status(response) shouldBe 521
      contentAsJson(response).as[Shuttering] shouldBe Shuttering(shuttered = true, "Gad Dangit!", "This service is shuttered")
    }
  }
}
