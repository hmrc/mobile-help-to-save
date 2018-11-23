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

import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OneInstancePerTest, OptionValues, WordSpec}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import play.api.test.Helpers.status
import uk.gov.hmrc.mobilehelptosave.{AccountTestData, TransactionTestData}
import uk.gov.hmrc.mobilehelptosave.domain.SavingsTarget
import uk.gov.hmrc.mobilehelptosave.scalatest.SchemaMatchers
import uk.gov.hmrc.mobilehelptosave.support.LoggerStub

//noinspection TypeAnnotation
class SavingsTargetSpec
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
{
  "putSavingsTarget" when {
    "logged in user's NINO matches NINO in URL" should {
      "return put the target value in the repo and respond with 204" in new AuthorisedTestScenario with HelpToSaveMocking {
        val amount  = 21.50
        val request = FakeRequest().withBody(SavingsTarget(amount))

        accountReturns(Right(Some(mobileHelpToSaveAccount)))
        putSavingsTargetExpects(nino.value, amount)
        val resultF = controller.putSavingsTarget(nino.value)(request)

        status(resultF) shouldBe 204
      }

      "targetAmount is greater than monthly savings limit" should {
        "respond with a 422 Unprocessable Entity" in new AuthorisedTestScenario with HelpToSaveMocking {
          val amount  = mobileHelpToSaveAccount.maximumPaidInThisMonth.doubleValue() + 1
          val request = FakeRequest().withBody(SavingsTarget(amount))

          accountReturns(Right(Some(mobileHelpToSaveAccount)))

          val resultF = controller.putSavingsTarget(nino.value)(request)

          status(resultF) shouldBe 422
        }
      }

      "targetAmount is less than 1" should {
        "respond with a 422 Unprocessable Entity" in new AuthorisedTestScenario with HelpToSaveMocking {
          val amount  = 0.9999
          val request = FakeRequest().withBody(SavingsTarget(amount))

          accountReturns(Right(Some(mobileHelpToSaveAccount)))
          val resultF = controller.putSavingsTarget(nino.value)(request)

          status(resultF) shouldBe 422
        }
      }
    }
  }

  "deleteSavingsTarget" when {
    "logged in user's NINO matches NINO in URL" should {
      "delete the target value from the repo and respond with 204" in new AuthorisedTestScenario with HelpToSaveMocking {
        deleteSavingsTargetExpects(nino)

        val resultF = controller.deleteSavingsTarget(nino.value)(FakeRequest())
        status(resultF) shouldBe 204
      }
    }
  }
}
