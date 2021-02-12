/*
 * Copyright 2021 HM Revenue & Customs
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

import eu.timepit.refined.auto._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OneInstancePerTest, OptionValues, WordSpec}
import play.api.libs.json.Json
import play.api.test.Helpers.{contentAsJson, status, _}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveGetTransactions
import uk.gov.hmrc.mobilehelptosave.controllers.{AlwaysAuthorisedWithIds, HelpToSaveController}
import uk.gov.hmrc.mobilehelptosave.domain.{Account, ErrorInfo}
import uk.gov.hmrc.mobilehelptosave.scalatest.SchemaMatchers
import uk.gov.hmrc.mobilehelptosave.services.{AccountService, HtsSavingsUpdateService, SavingsUpdateService}
import uk.gov.hmrc.mobilehelptosave.support.{LoggerStub, ShutteringMocking}
import uk.gov.hmrc.mobilehelptosave.{AccountTestData, TransactionTestData}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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
    with TestSupport
    with ShutteringMocking {

  "getAccount" should {
    "ensure user is logged in and has a NINO by checking permissions using AuthorisedWithIds" in {
      isForbiddenIfNotAuthorisedForUser { controller =>
        status(controller.getAccount(nino, "02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())) shouldBe FORBIDDEN
      }
    }
  }

  "getAccount" when {
    "logged in user's NINO matches NINO in URL" should {
      "return 200 with the users account information obtained by passing NINO to AccountService" in new AuthorisedTestScenario
        with HelpToSaveMocking {
        accountReturns(Right(Some(mobileHelpToSaveAccount)))

        val accountData = controller.getAccount(nino, "02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())
        status(accountData) shouldBe OK
        val jsonBody = contentAsJson(accountData)
        jsonBody shouldBe Json.toJson(mobileHelpToSaveAccount)
      }
    }

    "there is a savings goal associated with the NINO" should {
      "return the savings goal in the account structure" in new AuthorisedTestScenario with HelpToSaveMocking {
        accountReturns(Right(Some(mobileHelpToSaveAccount)))

        val accountData = controller.getAccount(nino, "02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())
        status(accountData) shouldBe OK
        val account = contentAsJson(accountData).validate[Account]
      }
    }

    "the user has no Help to Save account according to AccountService" should {
      "return 404" in new AuthorisedTestScenario with HelpToSaveMocking {

        accountReturns(Right(None))

        val resultF = controller.getAccount(nino, "02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())
        status(resultF) shouldBe 404
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "code").as[String] shouldBe "ACCOUNT_NOT_FOUND"
        (jsonBody \ "message")
          .as[String] shouldBe "No Help to Save account exists for the specified NINO"

        (slf4jLoggerStub.warn(_: String)) verify * never ()
      }
    }

    "AccountService returns an error" should {
      "return 500" in new AuthorisedTestScenario with HelpToSaveMocking {

        accountReturns(Left(ErrorInfo.General))

        val resultF = controller.getAccount(nino, "02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())
        status(resultF) shouldBe 500
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "code").as[String] shouldBe ErrorInfo.General.code
      }
    }

    "the NINO in the URL does not match the logged in user's NINO" should {
      "return 403" in new AuthorisedTestScenario {

        val resultF = controller.getAccount(otherNino, "02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())
        status(resultF) shouldBe 403
        (slf4jLoggerStub
          .warn(_: String)) verify s"Attempt by $nino to access $otherNino's data"
      }
    }

    "helpToSaveShuttered = true" should {
      """return 521 "shuttered": true""" in {
        val accountService            = mock[AccountService[Future]]
        val helpToSaveGetTransactions = mock[HelpToSaveGetTransactions[Future]]
        val controller = new HelpToSaveController(
          logger,
          accountService,
          helpToSaveGetTransactions,
          new AlwaysAuthorisedWithIds(nino, trueShuttering),
          new HtsSavingsUpdateService,
          stubControllerComponents()
        )

        val resultF = controller.getAccount(nino, "02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())
        status(resultF) shouldBe 521
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "shuttered").as[Boolean] shouldBe true
        (jsonBody \ "title").as[String]      shouldBe "Shuttered"
        (jsonBody \ "message")
          .as[String] shouldBe "HTS is currently not available"
      }
    }
  }
}
