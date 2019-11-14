/*
 * Copyright 2019 HM Revenue & Customs
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

import cats.syntax.either._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OneInstancePerTest, OptionValues, WordSpec}
import play.api.test.Helpers.status
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import uk.gov.hmrc.mobilehelptosave.domain.{ErrorInfo, SavingsGoal}
import uk.gov.hmrc.mobilehelptosave.scalatest.SchemaMatchers
import uk.gov.hmrc.mobilehelptosave.support.{LoggerStub, ShutteringMocking}
import uk.gov.hmrc.mobilehelptosave.{AccountTestData, TransactionTestData}

//noinspection TypeAnnotation
class SavingsGoalSpec
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
  "putSavingsGoal" when {
    "logged in user's NINO matches NINO in URL" should {
      "set the goal value in the repo and respond with 204" in new AuthorisedTestScenario with HelpToSaveMocking {
        val amount  = 21.50
        val request = FakeRequest().withBody(SavingsGoal(amount))

        setSavingsGoalReturns(nino, amount, ().asRight)
        val resultF = controller.putSavingsGoal(nino.value, journeyId)(request)

        status(resultF) shouldBe 204
      }

      "translate a validation error to a 422 Unprocessable Entity" in new AuthorisedTestScenario with HelpToSaveMocking {
        val amount  = mobileHelpToSaveAccount.maximumPaidInThisMonth.doubleValue() + 1
        val request = FakeRequest().withBody(SavingsGoal(amount))

        setSavingsGoalReturns(nino, amount, ErrorInfo.ValidationError("error message").asLeft)

        val resultF = controller.putSavingsGoal(nino.value, journeyId)(request)

        status(resultF) shouldBe 422
      }
    }
  }

  "deleteSavingsGoal" when {
    "logged in user's NINO matches NINO in URL" should {
      "delete the goal value from the repo and respond with 204" in new AuthorisedTestScenario with HelpToSaveMocking {
        deleteSavingsGoalExpects(nino)

        val resultF = controller.deleteSavingsGoal(nino.value, journeyId)(FakeRequest())
        status(resultF) shouldBe 204
      }
    }
  }
}
