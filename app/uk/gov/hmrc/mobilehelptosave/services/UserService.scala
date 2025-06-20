/*
 * Copyright 2023 HM Revenue & Customs
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

import cats.data.*
import cats.implicits.*
import play.api.LoggerLike
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.UserServiceConfig
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveEligibility, HelpToSaveEnrolmentStatus}
import uk.gov.hmrc.mobilehelptosave.domain.UserState.{apply as _, *}
import uk.gov.hmrc.mobilehelptosave.domain.*
import uk.gov.hmrc.mobilehelptosave.repository.EligibilityRepo

import java.time.temporal.ChronoUnit
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

trait UserService {
  def userDetails(nino: Nino)(implicit hc: HeaderCarrier): Future[Either[ErrorInfo, UserDetails]]
}

class HtsUserService(
  logger: LoggerLike,
  config: UserServiceConfig,
  helpToSaveEnrolmentStatus: HelpToSaveEnrolmentStatus,
  helpToSaveEligibility: HelpToSaveEligibility,
  eligibilityStatusRepo: EligibilityRepo
)(implicit ec: ExecutionContext)
    extends UserService {

  def userDetails(nino: Nino)(implicit hc: HeaderCarrier): Future[Either[ErrorInfo, UserDetails]] =
    (for {
      isEnrolled <- EitherT(helpToSaveEnrolmentStatus.enrolmentStatus())
      isEligible <- if (!isEnrolled && config.eligibilityCheckEnabled) EitherT(checkEligibility(nino))
                    else EitherT(Future.successful(false.asRight[ErrorInfo]))
      userDetails = (isEnrolled, isEligible) match {
                      case (true, _) => UserDetails(Enrolled)
                      case (_, true) => UserDetails(NotEnrolledButEligible)
                      case (_, _)    => UserDetails(NotEnrolled)
                    }
    } yield userDetails).value

  protected def checkEligibility(nino: Nino)(implicit hc: HeaderCarrier): Future[Either[ErrorInfo, Boolean]] =
    eligibilityStatusRepo.getEligibility(nino).flatMap {
      case Some(e) => Future.successful(e.eligible.asRight[ErrorInfo])
      case None =>
        EitherT(helpToSaveEligibility.checkEligibility())
          .map(r =>
            (r.eligibilityCheckResult.resultCode, r.eligibilityCheckResult.reasonCode) match {
              case (1, 6) => true
              case (1, 7) => true
              case (1, 8) => true
              case _      => false
            }
          )
          .flatMap(e =>
            EitherT.liftF[Future, ErrorInfo, Boolean](
              eligibilityStatusRepo.setEligibility(Eligibility(nino, e, expireAtTime)).map(_ => e)
            )
          )
          .value
    }

  private def expireAtTime: Instant = Instant.now().plus(28, ChronoUnit.DAYS)

}
