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

package uk.gov.hmrc.mobilehelptosave.services

import org.scalatest.{EitherValues, Matchers, WordSpec}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.EnabledInvitationFilters
import uk.gov.hmrc.mobilehelptosave.domain.ErrorInfo

import scala.concurrent.ExecutionContext.Implicits.{global => passedEc}
import scala.concurrent.{ExecutionContext, Future}

class InvitationEligibilityServiceSpec extends WordSpec with Matchers
  with FutureAwaits with DefaultAwaitTimeout with EitherValues {

  private val generator = new Generator(0)
  private val nino = generator.nextNino

  private implicit val passedHc: HeaderCarrier = HeaderCarrier()

  "userIsEligibleToBeInvited" when {
    "all filters are disabled" should {
      val enabledFilters = TestEnabledInvitationFilters(
        workingTaxCreditsInvitationFilter = false
      )

      "return true regardless of the user's characteristics" in {
        val service = new InvitationEligibilityServiceImpl(
          shouldNotBeCalledTaxCreditsService,
          enabledFilters
        )

        await(service.userIsEligibleToBeInvited(nino)).right.value shouldBe true
      }
    }

    "Working Tax Credits filter is enabled" should {
      val enabledFilters = TestEnabledInvitationFilters(
        workingTaxCreditsInvitationFilter = true
      )

      "return false if the user does not have recent WTC payments" in {
        val service = new InvitationEligibilityServiceImpl(
          fakeTaxCreditsService(nino, wtc = false),
          enabledFilters
        )

        await(service.userIsEligibleToBeInvited(nino)).right.value shouldBe false
      }

      "return true if the user has recent WTC payments" in {
        val service = new InvitationEligibilityServiceImpl(
          fakeTaxCreditsService(nino, wtc = true),
          enabledFilters
        )

        await(service.userIsEligibleToBeInvited(nino)).right.value shouldBe true
      }
    }
  }

  private def fakeTaxCreditsService(expectedNino: Nino, wtc: Boolean) = new TaxCreditsService {
    override def hasRecentWtcPayments(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Boolean]] = {
      nino shouldBe expectedNino
      hc shouldBe passedHc
      ec shouldBe passedEc

      Future successful Right(wtc)
    }
  }

  private val shouldNotBeCalledTaxCreditsService = new TaxCreditsService {
    override def hasRecentWtcPayments(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Boolean]] = Future failed new RuntimeException("TaxCreditsService should not be called in this situation")
  }

}

private case class TestEnabledInvitationFilters(
  workingTaxCreditsInvitationFilter: Boolean
) extends EnabledInvitationFilters
