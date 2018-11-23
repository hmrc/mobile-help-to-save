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

package uk.gov.hmrc.mobilehelptosave.controllers.helpToSave

import java.time.LocalDateTime

import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OneInstancePerTest, OptionValues, WordSpec}
import play.api.libs.json.Json
import play.api.test.Helpers.{contentAsJson, status, _}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveGetTransactions
import uk.gov.hmrc.mobilehelptosave.controllers.{AlwaysAuthorisedWithIds, HelpToSaveController}
import uk.gov.hmrc.mobilehelptosave.domain.{Account, ErrorInfo, SavingsTarget}
import uk.gov.hmrc.mobilehelptosave.repository.{SavingsTargetMongoModel, SavingsTargetRepo}
import uk.gov.hmrc.mobilehelptosave.scalatest.SchemaMatchers
import uk.gov.hmrc.mobilehelptosave.services.AccountService
import uk.gov.hmrc.mobilehelptosave.support.LoggerStub
import uk.gov.hmrc.mobilehelptosave.{AccountTestData, TransactionTestData}

//noinspection TypeAnnotation
class GetAccountSpec
  extends WordSpec
    with Matchers
    with SchemaMatchers
    with FutureAwaits
    with OptionValues
    with TransactionTestData
    with AccountTestData
    with DefaultAwaitTimeout
    with MockFactory
    with LoggerStub
    with OneInstancePerTest
    with TestSupport {

  "getAccount" should {
    "ensure user is logged in and has a NINO by checking permissions using AuthorisedWithIds" in {
      isForbiddenIfNotAuthorisedForUser { controller =>
        status(controller.getAccount(nino.value)(FakeRequest())) shouldBe FORBIDDEN
      }
    }
  }

  "getAccount" when {
    "logged in user's NINO matches NINO in URL" should {
      "return 200 with the users account information obtained by passing NINO to AccountService" in new AuthorisedTestScenario with HelpToSaveMocking {

        accountReturns(Right(Some(mobileHelpToSaveAccount)))

        savingsTargetReturns(nino, None)

        val accountData = controller.getAccount(nino.value)(FakeRequest())
        status(accountData) shouldBe OK
        val jsonBody = contentAsJson(accountData)
        jsonBody shouldBe Json.toJson(mobileHelpToSaveAccount)
      }
    }

    "there is a savings target associate with the NINO" should {
      "return the savings target in the account structure" in new AuthorisedTestScenario with HelpToSaveMocking {
        accountReturns(Right(Some(mobileHelpToSaveAccount)))
        val savingsTarget = 21.5
        savingsTargetReturns(nino, Some(SavingsTargetMongoModel(nino.value, 21.5, LocalDateTime.now())))

        val accountData = controller.getAccount(nino.value)(FakeRequest())
        status(accountData) shouldBe OK
        val account = contentAsJson(accountData).validate[Account]
        account.asOpt.value.savingsTarget.value shouldBe SavingsTarget(savingsTarget)
      }
    }

    "the user has no Help to Save account according to AccountService" should {
      "return 404" in new AuthorisedTestScenario with HelpToSaveMocking {

        accountReturns(Right(None))
        savingsTargetReturns(nino, None)

        val resultF = controller.getAccount(nino.value)(FakeRequest())
        status(resultF) shouldBe 404
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "code").as[String] shouldBe "ACCOUNT_NOT_FOUND"
        (jsonBody \ "message").as[String] shouldBe "No Help to Save account exists for the specified NINO"

        (slf4jLoggerStub.warn(_: String)) verify * never()
      }
    }

    "AccountService returns an error" should {
      "return 500" in new AuthorisedTestScenario with HelpToSaveMocking {

        accountReturns(Left(ErrorInfo("TEST_ERROR_CODE")))
        savingsTargetReturns(nino, None)

        val resultF = controller.getAccount(nino.value)(FakeRequest())
        status(resultF) shouldBe 500
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "code").as[String] shouldBe "TEST_ERROR_CODE"
      }
    }

    "the NINO in the URL does not match the logged in user's NINO" should {
      "return 403" in new AuthorisedTestScenario {

        val resultF = controller.getAccount(otherNino.value)(FakeRequest())
        status(resultF) shouldBe 403
        (slf4jLoggerStub.warn(_: String)) verify s"Attempt by ${nino.value} to access ${otherNino.value}'s data"
      }
    }

    "the NINO is not in the correct format" should {
      "return 400 NINO_INVALID" in new AuthorisedTestScenario {

        val resultF = controller.getAccount("invalidNino")(FakeRequest())
        status(resultF) shouldBe 400
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "code").as[String] shouldBe "NINO_INVALID"
        (jsonBody \ "message").as[String] shouldBe """"invalidNino" does not match NINO validation regex"""
      }
    }

    "the NINO in the URL contains spaces" should {
      "return 400 NINO_INVALID" in new AuthorisedTestScenario {

        val resultF = controller.getAccount("AA 00 00 03 D")(FakeRequest())
        status(resultF) shouldBe 400
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "code").as[String] shouldBe "NINO_INVALID"
        (jsonBody \ "message").as[String] shouldBe """"AA 00 00 03 D" does not match NINO validation regex"""
      }
    }

    "helpToSaveShuttered = true" should {
      """return 521 "shuttered": true""" in {
        val accountService = mock[AccountService]
        val helpToSaveGetTransactions = mock[HelpToSaveGetTransactions]
        val savingsTargetRepo = mock[SavingsTargetRepo]
        val controller = new HelpToSaveController(logger, accountService, helpToSaveGetTransactions, new AlwaysAuthorisedWithIds(nino), config.copy(shuttering = trueShuttering), savingsTargetRepo)

        val resultF = controller.getAccount(nino.value)(FakeRequest())
        status(resultF) shouldBe 521
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "shuttered").as[Boolean] shouldBe true
        (jsonBody \ "title").as[String] shouldBe "Shuttered"
        (jsonBody \ "message").as[String] shouldBe "HTS is currently not available"
      }
    }

    "the savingsGoalsEnabled flag configured as true" should {
      "return true in the Account" in new AuthorisedTestScenario with HelpToSaveMocking {
        accountReturns(Right(Some(mobileHelpToSaveAccount)))

        override val controller =
          new HelpToSaveController(
            logger,
            accountService,
            helpToSaveGetTransactions,
            new AlwaysAuthorisedWithIds(nino),
            config.copy(savingsGoalsEnabled = true),
            savingsTargetRepo)

        savingsTargetReturns(nino, None)

        val accountData = controller.getAccount(nino.value)(FakeRequest())
        status(accountData) shouldBe OK
        val jsonBody = contentAsJson(accountData)
        (jsonBody \ "savingsGoalsEnabled").as[Boolean] shouldBe true
      }
    }

    "the savingsGoalsEnabled flag is configured as false" should {
      "return false in the Account" in new AuthorisedTestScenario with HelpToSaveMocking {
        accountReturns(Right(Some(mobileHelpToSaveAccount)))

        savingsTargetReturns(nino, None)

        val accountData = controller.getAccount(nino.value)(FakeRequest())
        status(accountData) shouldBe OK
        val jsonBody = contentAsJson(accountData)
        (jsonBody \ "savingsGoalsEnabled").as[Boolean] shouldBe false
      }
    }
  }
}
