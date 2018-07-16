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
import org.joda.time.DateTimeZone
import play.api.LoggerLike
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.config.UserServiceConfig
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveConnectorEnrolmentStatus
import uk.gov.hmrc.mobilehelptosave.domain.UserState.{Value, apply => _, _}
import uk.gov.hmrc.mobilehelptosave.domain.{InternalAuthId, UserState, _}
import uk.gov.hmrc.mobilehelptosave.metrics.MobileHelpToSaveMetrics
import uk.gov.hmrc.mobilehelptosave.repos.InvitationRepository

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserService @Inject()(
                             logger: LoggerLike,
                             invitationEligibilityService: InvitationEligibilityService,
                             helpToSaveConnector: HelpToSaveConnectorEnrolmentStatus,
                             metrics: MobileHelpToSaveMetrics,
                             invitationRepository: InvitationRepository,
                             accountService: AccountService,
                             clock: Clock,
                             config: UserServiceConfig
                           ) {


  private sealed trait FlowControl
  private case class Complete(state:UserState.Value) extends FlowControl
  private case object Continue extends FlowControl

  private type ErrorOrUserState = EitherT[Future, ErrorInfo, UserState.Value]
  private type NextStep = EitherT[Future, ErrorInfo, FlowControl]

  private def continueRunningSteps(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, ErrorInfo, FlowControl] = {
    EitherT.right[ErrorInfo](successful(Continue))
  }

  private def completeWithUserState(state: UserState.Value)(implicit hc: HeaderCarrier, ec: ExecutionContext): NextStep = {
    EitherT.right[ErrorInfo](successful(Complete(state)))
  }

  private def isAlreadyEnrolled(implicit hc: HeaderCarrier, ec: ExecutionContext) : NextStep = {
    EitherT(helpToSaveConnector.enrolmentStatus()).flatMap{
      case isEnrolled if isEnrolled => completeWithUserState(Enrolled)
      case _  => continueRunningSteps
    }
  }

  private def isUserEligibleToBeInvited(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext) : NextStep = {
    EitherT(invitationEligibilityService.userIsEligibleToBeInvited(nino)).flatMap{
      case isEligible if isEligible => continueRunningSteps
      case _  => completeWithUserState(NotEnrolled)
    }
  }

  private def isUserInvited(internalAuthId: InternalAuthId)(implicit hc: HeaderCarrier, ec: ExecutionContext) : NextStep = {
    EitherT.right[ErrorInfo](invitationRepository.findById(internalAuthId).map(_.isDefined)).flatMap{
      case isInvited if isInvited => completeWithUserState(Invited)
      case _  => continueRunningSteps
    }
  }

  private def isWithinDailyInviteLimit(implicit hc: HeaderCarrier, ec: ExecutionContext): NextStep = {
    EitherT.right[ErrorInfo](invitationRepository.countCreatedSince(startOfTodayUtc()).map(_ >= config.dailyInvitationCap)).flatMap {
      case reachedCapLimit if reachedCapLimit => completeWithUserState(NotEnrolled)
      case _  => continueRunningSteps
    }
  }

  private def inviteUser(nino: Nino, internalAuthId: InternalAuthId)(implicit hc: HeaderCarrier, ec: ExecutionContext): ErrorOrUserState = {
    EitherT.right[ErrorInfo](invitationRepository.insert(Invitation(internalAuthId, clock.now())).map { _ =>
      metrics.invitationCounter.inc()
      InvitedFirstTime
    }.recover {
      case e: DatabaseException if invitationRepository.isDuplicateKey(e) => Invited
    })
  }

  private def evaluateStateForUser(nino: Nino, internalAuthId: InternalAuthId)(implicit hc: HeaderCarrier, ec: ExecutionContext): ErrorOrUserState = {

    def step(runStep: => NextStep)(continue:  => ErrorOrUserState) : ErrorOrUserState = {
      runStep.flatMap {
        case Complete(state) => EitherT.pure[Future, ErrorInfo](state)
        case Continue  => continue
      }
    }

    step(isAlreadyEnrolled) {
      step(isUserInvited(internalAuthId)) {
        step(isWithinDailyInviteLimit) {
          step(isUserEligibleToBeInvited(nino)) {
            inviteUser(nino, internalAuthId)
          }
        }
      }
    }
  }

  def userDetails(internalAuthId: InternalAuthId, nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, UserDetails]] = {
    evaluateStateForUser(nino, internalAuthId)
      .flatMap(state => EitherT.right[ErrorInfo](userDetails(nino, state)))
      .map(identity)
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

  private def startOfTodayUtc() = clock.now().withZone(DateTimeZone.UTC).withTimeAtStartOfDay()
}
