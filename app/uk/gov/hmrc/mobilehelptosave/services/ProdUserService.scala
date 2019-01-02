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

package uk.gov.hmrc.mobilehelptosave.services

import cats.data._
import cats.implicits._
import play.api.LoggerLike
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveEnrolmentStatus
import uk.gov.hmrc.mobilehelptosave.domain.UserState.{apply => _, _}
import uk.gov.hmrc.mobilehelptosave.domain._

import scala.concurrent.{ExecutionContext, Future}

trait UserService[F[_]] {
  def userDetails(nino: Nino)(implicit hc: HeaderCarrier): F[Either[ErrorInfo, UserDetails]]
}

class ProdUserService(
  logger:              LoggerLike,
  helpToSaveConnector: HelpToSaveEnrolmentStatus[Future]
)(implicit ec:         ExecutionContext)
    extends UserService[Future] {
  def userDetails(nino: Nino)(implicit hc: HeaderCarrier): Future[Either[ErrorInfo, UserDetails]] =
    EitherT(helpToSaveConnector.enrolmentStatus())
      .map(isEnrolled => if (isEnrolled) Enrolled else NotEnrolled)
      .map(state => UserDetails(state = state))
      .value
}
