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

package uk.gov.hmrc.mobilehelptosave.controllers.helpToSave

import eu.timepit.refined.auto.*
import org.mockito.Mockito.verify
import org.scalatest.{OneInstancePerTest, OptionValues}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.test.Helpers.{contentAsJson, status, *}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveGetTransactions
import uk.gov.hmrc.mobilehelptosave.controllers.{AlwaysAuthorisedWithIds, HelpToSaveController}
import uk.gov.hmrc.mobilehelptosave.domain.ErrorInfo
import uk.gov.hmrc.mobilehelptosave.domain.types.JourneyId
import uk.gov.hmrc.mobilehelptosave.repository.SavingsGoalEventRepo
import uk.gov.hmrc.mobilehelptosave.services.{AccountService, HtsSavingsUpdateService}
import uk.gov.hmrc.mobilehelptosave.support.{LoggerStub, ShutteringMocking}
import uk.gov.hmrc.mobilehelptosave.{AccountTestData, TransactionTestData}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GetTransactionsSpec
    extends AnyWordSpecLike
      with Matchers
      with FutureAwaits
      with OptionValues
      with TransactionTestData
      with AccountTestData
      with DefaultAwaitTimeout
      with MockitoSugar
      with LoggerStub
      with OneInstancePerTest
      with TestSupport
      with ShutteringMocking {

  val jid: JourneyId = JourneyId.from("02940b73-19cc-4c31-80d3-f4deb851c707").toOption.get

  "getTransactions" should {
    "ensure user is logged in and has a NINO by checking permissions using AuthorisedWithIds" in {
      isForbiddenIfNotAuthorisedForUser { controller =>
        status(controller.getTransactions(nino, jid)(FakeRequest())) shouldBe FORBIDDEN
      }
    }
  }

  "getTransactions" when {
    "logged in user's NINO matches NINO in URL" should {
      "return 200 with transactions obtained by passing NINO to the HelpToSaveConnector" in new AuthorisedTestScenario
        with HelpToSaveMocking {

        helpToSaveGetTransactionsReturns(Future successful Right(transactionsSortedInHelpToSaveOrder))

        val resultF = controller.getTransactions(nino, jid)(FakeRequest())
        status(resultF) shouldBe 200
        val jsonBody = contentAsJson(resultF)
        jsonBody shouldBe Json.toJson(transactionsSortedInMobileHelpToSaveOrder)
      }
    }

    "no account is not found by HelpToSaveConnector for the NINO" should {
      "return 404" in new AuthorisedTestScenario with HelpToSaveMocking {

        helpToSaveGetTransactionsReturns(Future successful Left(ErrorInfo.AccountNotFound))

        val resultF = controller.getTransactions(nino, jid)(FakeRequest())
        status(resultF) shouldBe 404
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "code").as[String]    shouldBe "ACCOUNT_NOT_FOUND"
        (jsonBody \ "message").as[String] shouldBe "No Help to Save account exists for the specified NINO"
      }
    }

    "HelpToSaveConnector returns an error" should {
      "return 500" in new AuthorisedTestScenario with HelpToSaveMocking {

        helpToSaveGetTransactionsReturns(Future successful Left(ErrorInfo.General))

        val resultF = controller.getTransactions(nino, jid)(FakeRequest())
        status(resultF) shouldBe 500
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "code").as[String] shouldBe ErrorInfo.General.code
      }
    }

    "the NINO in the URL does not match the logged in user's NINO" should {
      "return 403" in new AuthorisedTestScenario {

        val resultF = controller.getTransactions(otherNino, jid)(FakeRequest())
        status(resultF) shouldBe 403
        verify(slf4jLoggerStub).warn(s"Attempt by $nino to access $otherNino's data")
      }
    }

    "helpToSaveShuttered = true" should {
      """return 521 "shuttered": true""" in {
        val accountService            = mock[AccountService]
        val helpToSaveGetTransactions = mock[HelpToSaveGetTransactions]
        val savingsGoalEventRepo      = mock[SavingsGoalEventRepo]
        val controller = new HelpToSaveController(
          logger,
          accountService,
          helpToSaveGetTransactions,
          new AlwaysAuthorisedWithIds(nino, trueShuttering),
          new HtsSavingsUpdateService,
          savingsGoalEventRepo,
          stubControllerComponents()
        )

        val resultF = controller.getTransactions(nino, jid)(FakeRequest())
        status(resultF) shouldBe 521
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "shuttered").as[Boolean] shouldBe true
        (jsonBody \ "title").as[String]      shouldBe "Shuttered"
        (jsonBody \ "message").as[String]    shouldBe "HTS is currently not available"
      }
    }
  }
}
