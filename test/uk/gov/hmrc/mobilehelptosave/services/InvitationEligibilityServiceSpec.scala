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

import org.scalatest.{Matchers, OptionValues, WordSpec}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.EnabledInvitationFilters

import scala.concurrent.ExecutionContext.Implicits.{global => passedEc}
import scala.concurrent.{ExecutionContext, Future}

class InvitationEligibilityServiceSpec extends WordSpec with Matchers
  with FutureAwaits with DefaultAwaitTimeout with OptionValues {

  private val generator = new Generator(0)
  private val nino = generator.nextNino

  private implicit val passedHc: HeaderCarrier = HeaderCarrier()

  "userIsEligibleToBeInvited" when {
    "all filters are disabled" should {
      val enabledFilters = EnabledInvitationFilters(
        surveyInvitationFilter = false,
        workingTaxCreditsInvitationFilter = false
      )

      "return true regardless of the user's characteristics" in {
        val service = new InvitationEligibilityServiceImpl(
          shouldNotBeCalledSurveyService,
          shouldNotBeCalledTaxCreditsService,
          enabledFilters
        )

        await(service.userIsEligibleToBeInvited(nino)).value shouldBe true
      }
    }

    "survey invitation filter is enabled" should {
      val enabledFilters = EnabledInvitationFilters(
        surveyInvitationFilter = true,
        workingTaxCreditsInvitationFilter = false
      )

      "return false if the user has not said they wanted to be contacted in the survey" in {
        val service = new InvitationEligibilityServiceImpl(
          fakeSurveyService(wantsToBeContacted = Some(false)),
          shouldNotBeCalledTaxCreditsService,
          enabledFilters
        )

        await(service.userIsEligibleToBeInvited(nino)).value shouldBe false
      }

      "return true if the user has said they wanted to be contacted in the survey" in {
        val service = new InvitationEligibilityServiceImpl(
          fakeSurveyService(wantsToBeContacted = Some(true)),
          shouldNotBeCalledTaxCreditsService,
          enabledFilters
        )

        await(service.userIsEligibleToBeInvited(nino)).value shouldBe true
      }
    }

    "Working Tax Credits filter is enabled" should {
      val enabledFilters = EnabledInvitationFilters(
        surveyInvitationFilter = false,
        workingTaxCreditsInvitationFilter = true
      )

      "return false if the user does not have recent WTC payments" in {
        val service = new InvitationEligibilityServiceImpl(
          shouldNotBeCalledSurveyService,
          fakeTaxCreditsService(nino, wtc = false),
          enabledFilters
        )

        await(service.userIsEligibleToBeInvited(nino)).value shouldBe false
      }

      "return true if the user has recent WTC payments" in {
        val service = new InvitationEligibilityServiceImpl(
          shouldNotBeCalledSurveyService,
          fakeTaxCreditsService(nino, wtc = true),
          enabledFilters
        )

        await(service.userIsEligibleToBeInvited(nino)).value shouldBe true
      }
    }

    "both filters are enabled" should {
      val enabledFilters = EnabledInvitationFilters(
        surveyInvitationFilter = true,
        workingTaxCreditsInvitationFilter = true
      )

      "return false when survey filter accepts user but WTC filter rejects them" in {
        val service = new InvitationEligibilityServiceImpl(
          fakeSurveyService(wantsToBeContacted = Some(true)),
          fakeTaxCreditsService(nino, wtc = false),
          enabledFilters
        )

        await(service.userIsEligibleToBeInvited(nino)).value shouldBe false
      }
    }
  }

  private def fakeSurveyService(wantsToBeContacted: Option[Boolean]) = new SurveyService {
    override def userWantsToBeContacted()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] = {
      hc shouldBe passedHc
      ec shouldBe passedEc

      Future successful wantsToBeContacted
    }
  }

  private val shouldNotBeCalledSurveyService = new SurveyService {
    override def userWantsToBeContacted()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] = Future failed new RuntimeException("SurveyService should not be called in this situation")
  }

  private def fakeTaxCreditsService(expectedNino: Nino, wtc: Boolean) = new TaxCreditsService {
    override def hasRecentWtcPayments(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] = {
      nino shouldBe expectedNino
      hc shouldBe passedHc
      ec shouldBe passedEc

      Future successful Some(wtc)
    }
  }

  private val shouldNotBeCalledTaxCreditsService = new TaxCreditsService {
    override def hasRecentWtcPayments(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] = Future failed new RuntimeException("TaxCreditsService should not be called in this situation")
  }

}
