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
import play.api.libs.json.{JsArray, JsLookupResult, JsValue}
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
  private val config = TestHelpToSaveControllerConfig(shuttering, 50.0)
  private val currentTime = new DateTime(2018, 9, 29, 12, 30, DateTimeZone.forID("Europe/London"))
  private val fixedClock = new FixedFakeClock(currentTime)
  private val controller: SandboxController = new SandboxController(logger, config, SandboxData(logger, fixedClock, TestSandboxDataConfig))
  private val shutteredController: SandboxController = new SandboxController(logger, config.copy(shuttering = shutteredShuttering), SandboxData(logger, fixedClock, TestSandboxDataConfig))

  implicit class TransactionJson(json: JsValue) {
    def operation(transactionIndex: Int): String = ((json \ "transactions") (transactionIndex) \ "operation").as[String]
    def amount(transactionIndex: Int): BigDecimal = ((json \ "transactions") (transactionIndex) \ "amount").as[BigDecimal]
    def transactionDate(transactionIndex: Int): String = ((json \ "transactions") (transactionIndex) \ "transactionDate").as[String]
    def accountingDate(transactionIndex: Int): String = ((json \ "transactions") (transactionIndex) \ "accountingDate").as[String]
    def balanceAfter(transactionIndex: Int): BigDecimal = ((json \ "transactions") (transactionIndex) \ "balanceAfter").as[BigDecimal]
    def transactionCount(): Int = (json \ "transactions").as[JsArray].value.length
  }

  "Sandbox getTransactions" should {
    "return the sandbox transaction data" in {

      val response: Future[Result] = controller.getTransactions(nino.value)(FakeRequest())

      status(response) shouldBe OK
      val json: JsValue = contentAsJson(response)

      json transactionCount() shouldBe 11

      var atIndex = 0
      json operation atIndex shouldBe "credit"
      json amount atIndex shouldBe BigDecimal(20.5)
      json transactionDate atIndex shouldBe "2018-09-29"
      json accountingDate atIndex shouldBe "2018-09-29"
      json balanceAfter atIndex shouldBe BigDecimal(220.5)

      atIndex = 1
      json operation atIndex shouldBe "credit"
      json amount atIndex shouldBe BigDecimal(20)
      json transactionDate atIndex shouldBe "2018-08-29"
      json accountingDate atIndex shouldBe "2018-08-29"
      json balanceAfter atIndex shouldBe BigDecimal(200)

      atIndex = 2
      json operation atIndex shouldBe "credit"
      json amount atIndex shouldBe BigDecimal(18.2)
      json transactionDate atIndex shouldBe "2018-08-29"
      json accountingDate atIndex shouldBe "2018-08-29"
      json balanceAfter atIndex shouldBe BigDecimal(180)

      atIndex = 3
      json operation atIndex shouldBe "credit"
      json amount atIndex shouldBe BigDecimal(10.4)
      json transactionDate atIndex shouldBe "2018-07-29"
      json accountingDate atIndex shouldBe "2018-07-29"
      json balanceAfter atIndex shouldBe BigDecimal(161.8)

      atIndex = 4
      json operation atIndex shouldBe "credit"
      json amount atIndex shouldBe BigDecimal(35)
      json transactionDate atIndex shouldBe "2018-06-29"
      json accountingDate atIndex shouldBe "2018-06-29"
      json balanceAfter atIndex shouldBe BigDecimal(151.4)

      atIndex = 5
      json operation atIndex shouldBe "credit"
      json amount atIndex shouldBe BigDecimal(15)
      json transactionDate atIndex shouldBe "2018-06-29"
      json accountingDate atIndex shouldBe "2018-06-29"
      json balanceAfter atIndex shouldBe BigDecimal(116.4)

      atIndex = 6
      json operation atIndex shouldBe "credit"
      json amount atIndex shouldBe BigDecimal(6)
      json transactionDate atIndex shouldBe "2018-05-29"
      json accountingDate atIndex shouldBe "2018-05-29"
      json balanceAfter atIndex shouldBe BigDecimal(101.4)

      atIndex = 7
      json operation atIndex shouldBe "credit"
      json amount atIndex shouldBe BigDecimal(20.4)
      json transactionDate atIndex shouldBe "2018-04-29"
      json accountingDate atIndex shouldBe "2018-04-29"
      json balanceAfter atIndex shouldBe BigDecimal(95.4)

      atIndex = 8
      json operation atIndex shouldBe "credit"
      json amount atIndex shouldBe BigDecimal(10)
      json transactionDate atIndex shouldBe "2018-04-29"
      json accountingDate atIndex shouldBe "2018-04-29"
      json balanceAfter atIndex shouldBe BigDecimal(75)

      atIndex = 9
      json operation atIndex shouldBe "credit"
      json amount atIndex shouldBe BigDecimal(25)
      json transactionDate atIndex shouldBe "2018-03-29"
      json accountingDate atIndex shouldBe "2018-03-29"
      json balanceAfter atIndex shouldBe BigDecimal(65)

      atIndex = 10
      json operation atIndex shouldBe "credit"
      json amount atIndex shouldBe BigDecimal(40)
      json transactionDate atIndex shouldBe "2018-02-28"
      json accountingDate atIndex shouldBe "2018-02-28"
      json balanceAfter atIndex shouldBe BigDecimal(40)
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

      (json \ "inAppPaymentsEnabled").as[Boolean] shouldBe false
    }

    "return a shuttered response when the service is shuttered" in {

      val response: Future[Result] = shutteredController.getAccount(nino.value)(FakeRequest())
      status(response) shouldBe 521
      contentAsJson(response).as[Shuttering] shouldBe Shuttering(shuttered = true, "Gad Dangit!", "This service is shuttered")
    }
  }
}

object TestSandboxDataConfig extends SandboxDataConfig {
  override val inAppPaymentsEnabled: Boolean = false
}