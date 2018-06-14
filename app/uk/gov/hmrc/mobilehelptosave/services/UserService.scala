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
import cats.syntax.either._
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTimeZone
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.config.UserServiceConfig
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveConnectorEnrolmentStatus
import uk.gov.hmrc.mobilehelptosave.domain.{UserState, _}
import uk.gov.hmrc.mobilehelptosave.metrics.MobileHelpToSaveMetrics
import uk.gov.hmrc.mobilehelptosave.repos.InvitationRepository

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserService @Inject() (
  invitationEligibilityService: InvitationEligibilityService,
  helpToSaveConnector: HelpToSaveConnectorEnrolmentStatus,
  metrics: MobileHelpToSaveMetrics,
  invitationRepository: InvitationRepository,
  accountService: AccountService,
  clock: Clock,
  config: UserServiceConfig
) {

  def userDetails(internalAuthId: InternalAuthId, nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Option[UserDetails]]] = whenEnabled {
    (for {
      enrolled <- EitherT(helpToSaveConnector.enrolmentStatus())
      state <- EitherT(determineState(internalAuthId, nino, enrolled))
      userDetails <- EitherT.right[ErrorInfo](userDetails(nino, state))
    } yield {
      userDetails
    }).value
  }

  private def whenEnabled[T](body: => Future[Either[ErrorInfo, UserDetails]])(implicit ec: ExecutionContext): Future[Either[ErrorInfo, Option[UserDetails]]] =
    if (config.helpToSaveEnabled) {
      EitherT(body).map(Some.apply).value
    } else {
      Future successful Right(None)
    }

  private def userDetails(nino: Nino, state: UserState.Value)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[UserDetails] = {
    val accountFEO: Future[Either[ErrorInfo, Option[Account]]] = if (state == UserState.Enrolled && anyAccountFeatureEnabled) {
      accountService.account(nino).map(_.map(Some.apply))
    } else {
      Future successful Right(None)
    }

    accountFEO.map { accountEO =>
      val (accountO, accountErrorO) = accountEO match {
        case Right(aO) => (aO, None)
        case Left(accountError) => (None, Some(accountError))
      }
      UserDetails(state = state, account = accountO, accountError = accountErrorO)
    }
  }

  private def anyAccountFeatureEnabled: Boolean =
    config.balanceEnabled || config.paidInThisMonthEnabled || config.firstBonusEnabled

  private def determineState(internalAuthId: InternalAuthId, nino: Nino, enrolled: Boolean)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, UserState.Value]] =
    if (enrolled) {
      Future successful Right(UserState.Enrolled)
    } else {
      EitherT(invitationEligibilityService.userIsEligibleToBeInvited(nino)).flatMap { eligibleToBeInvited =>
        if (eligibleToBeInvited) EitherT.right[ErrorInfo](determineInvitedState(internalAuthId))
        else EitherT.pure[Future, ErrorInfo](UserState.NotEnrolled)
      }.value
    }

  private def determineInvitedState(internalAuthId: InternalAuthId)(implicit ec: ExecutionContext): Future[UserState.Value] =
    invitationRepository.findById(internalAuthId).flatMap { invitationO =>
      if (invitationO.isDefined) {
        Future.successful(UserState.Invited)
      } else {
        invitationRepository.countCreatedSince(startOfTodayUtc()).flatMap { alreadyCreatedTodayCount =>
          if (alreadyCreatedTodayCount >= config.dailyInvitationCap) {
            Future.successful(UserState.NotEnrolled)
          } else {
            invitationRepository.insert(Invitation(internalAuthId, clock.now()))
              .map { _ =>
                metrics.invitationCounter.inc()
                UserState.InvitedFirstTime
              }
              .recover {
                case e: DatabaseException if invitationRepository.isDuplicateKey(e) =>
                  UserState.Invited
              }
          }
        }
      }
    }

  private def startOfTodayUtc() = clock.now().withZone(DateTimeZone.UTC).withTimeAtStartOfDay()
}
