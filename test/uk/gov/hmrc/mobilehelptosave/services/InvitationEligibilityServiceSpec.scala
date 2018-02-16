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
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.EnabledInvitationFilters

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class InvitationEligibilityServiceSpec extends WordSpec with Matchers with FutureAwaits with DefaultAwaitTimeout with OptionValues {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  "userIsEligibleToBeInvited" when {
    "survey invitation filter is disabled" should {
      val enabledFilters = EnabledInvitationFilters(surveyInvitationFilter = false)
      "return true regardless of the user's survey answers" in {
        val service = new InvitationEligibilityServiceImpl(
          shouldNotBeCalledSurveyService,
          enabledFilters
        )

        await(service.userIsEligibleToBeInvited()).value shouldBe true
      }
    }

    "survey invitation filter is enabled" should {
      val enabledFilters = EnabledInvitationFilters(surveyInvitationFilter = true)

      "return false if the user has not said they wanted to be contacted in the survey" in {
        val service = new InvitationEligibilityServiceImpl(
          fakeSurveyService(wantsToBeContacted = Some(false)),
          enabledFilters
        )

        await(service.userIsEligibleToBeInvited()).value shouldBe false
      }

      "return true if the user has said they wanted to be contacted in the survey" in {
        val service = new InvitationEligibilityServiceImpl(
          fakeSurveyService(wantsToBeContacted = Some(true)),
          enabledFilters
        )

        await(service.userIsEligibleToBeInvited()).value shouldBe true
      }
    }
  }

  private def fakeSurveyService(wantsToBeContacted: Option[Boolean]) = new SurveyService {
    override def userWantsToBeContacted()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] = Future successful wantsToBeContacted
  }

  private val shouldNotBeCalledSurveyService = new SurveyService {
    override def userWantsToBeContacted()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] = Future failed new RuntimeException("SurveyService should not be called in this situation")
  }

}
