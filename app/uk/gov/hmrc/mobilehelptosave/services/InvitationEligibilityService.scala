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

import cats.data.EitherT
import cats.instances.future._
import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.EnabledInvitationFilters
import uk.gov.hmrc.mobilehelptosave.domain.ErrorInfo

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[InvitationEligibilityServiceImpl])
trait InvitationEligibilityService {
  def userIsEligibleToBeInvited(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Boolean]]
}

@Singleton
class InvitationEligibilityServiceImpl @Inject() (
  surveyService: SurveyService,
  taxCreditsService: TaxCreditsService,
  enabledFilters: EnabledInvitationFilters
) extends InvitationEligibilityService {

  override def userIsEligibleToBeInvited(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Boolean]] = {
    val trueFE = Future successful Right(true)

    val surveyFE = if (enabledFilters.surveyInvitationFilter) surveyService.userWantsToBeContacted()
    else trueFE

    val wtcFE = if (enabledFilters.workingTaxCreditsInvitationFilter) taxCreditsService.hasRecentWtcPayments(nino)
    else trueFE

    (for {
      survey <- EitherT(surveyFE)
      wtc <- EitherT(wtcFE)
    } yield {
      survey && wtc
    }).value
  }
}
