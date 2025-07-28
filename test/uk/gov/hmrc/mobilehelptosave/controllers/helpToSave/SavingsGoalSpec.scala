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

import cats.syntax.either.*
import eu.timepit.refined.auto.*
import org.scalatest.{OneInstancePerTest, OptionValues}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.Helpers.{contentAsString, status}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import uk.gov.hmrc.mobilehelptosave.domain.types.JourneyId
import uk.gov.hmrc.mobilehelptosave.domain.{ErrorInfo, SavingsGoal}
import uk.gov.hmrc.mobilehelptosave.support.{LoggerStub, ShutteringMocking}
import uk.gov.hmrc.mobilehelptosave.{AccountTestData, TransactionTestData}

class SavingsGoalSpec
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


  "putSavingsGoal" when {
    "logged in user's NINO matches NINO in URL" should {
      "set the goal value in the repo and respond with 204" in new AuthorisedTestScenario with HelpToSaveMocking {
        val amount  = 21.50
        val request = FakeRequest().withBody(SavingsGoal(Some(amount)))

        setSavingsGoalReturns(nino, Some(amount), ().asRight)
        val resultF = controller.putSavingsGoal(nino, jid)(request)

        status(resultF) shouldBe 204
      }

      "set the goal Bad Request if both amount and name are missing" in new AuthorisedTestScenario
        with HelpToSaveMocking {
        val request = FakeRequest().withBody(SavingsGoal())

        val resultF = controller.putSavingsGoal(nino, jid)(request)

        status(resultF)          shouldBe 400
        contentAsString(resultF) shouldBe "Invalid savings goal combination"
      }

      "translate a validation error to a 422 Unprocessable Entity" in new AuthorisedTestScenario
        with HelpToSaveMocking {
        val amount  = mobileHelpToSaveAccount.maximumPaidInThisMonth.doubleValue + 1
        val request = FakeRequest().withBody(SavingsGoal(Some(amount)))

        setSavingsGoalReturns(nino, Some(amount), ErrorInfo.ValidationError("error message").asLeft)

        val resultF = controller.putSavingsGoal(nino, jid)(request)

        status(resultF) shouldBe 422
      }
    }
  }

  "deleteSavingsGoal" when {
    "logged in user's NINO matches NINO in URL" should {
      "delete the goal value from the repo and respond with 204" in new AuthorisedTestScenario with HelpToSaveMocking {
        deleteSavingsGoalExpects(nino)

        val resultF = controller.deleteSavingsGoal(nino, jid)(FakeRequest())
        status(resultF) shouldBe 204
      }
    }
  }
}
