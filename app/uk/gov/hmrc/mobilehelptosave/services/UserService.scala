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

import cats.data._
import cats.implicits._
import javax.inject.{Inject, Singleton}
import play.api.LoggerLike
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.UserServiceConfig
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveConnectorEnrolmentStatus
import uk.gov.hmrc.mobilehelptosave.domain.UserState.{Value, apply => _, _}
import uk.gov.hmrc.mobilehelptosave.domain.{InternalAuthId, _}

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserService @Inject()(
                             logger: LoggerLike,
                             helpToSaveConnector: HelpToSaveConnectorEnrolmentStatus,
                             accountService: AccountService,
                             config: UserServiceConfig
                           ) {

  def userDetails(internalAuthId: InternalAuthId, nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, UserDetails]] = {
    EitherT(helpToSaveConnector.enrolmentStatus())
      .map(isEnrolled => if (isEnrolled) Enrolled else NotEnrolled)
      .flatMap(state => EitherT.right[ErrorInfo](userDetails(nino, state)))
      .value
  }

  private def userDetails(nino: Nino, state: Value)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[UserDetails] = {
    if (state == Enrolled && anyAccountFeatureEnabled) {
      accountService.account(nino).map {
        case Right(None) =>
          logger.warn(s"$nino was enrolled according to help-to-save microservice but no account was found in NS&I - data is inconsistent")
          UserDetails(state = state, account = None, accountError = Some(ErrorInfo.General))
        case Right(accountOption) =>
          UserDetails(state = state, account = accountOption, accountError = None)
        case Left(accountError) =>
          UserDetails(state = state, account = None, accountError = Some(accountError))
      }
    } else {
      successful(UserDetails(state = state, account = None, accountError = None))
    }
  }

  private def anyAccountFeatureEnabled: Boolean =
    config.balanceEnabled || config.paidInThisMonthEnabled || config.firstBonusEnabled
}
