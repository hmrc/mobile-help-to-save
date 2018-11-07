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
import play.api.LoggerLike
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.AccountServiceConfig
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveEnrolmentStatus, HelpToSaveGetAccount}
import uk.gov.hmrc.mobilehelptosave.domain._

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveAccountService])
trait AccountService {

  def account(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Option[Account]]]

}

@Singleton
class HelpToSaveAccountService @Inject() (
  logger: LoggerLike,
  helpToSaveEnrolmentStatus: HelpToSaveEnrolmentStatus,
  helpToSaveGetAccount: HelpToSaveGetAccount,
  config: AccountServiceConfig
) extends AccountService {

  override def account(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Option[Account]]] =
    EitherT(helpToSaveEnrolmentStatus.enrolmentStatus()).flatMap {
      case true =>
        EitherT(helpToSaveGetAccount.getAccount(nino)).map {
          case Some(helpToSaveAccount) =>
            Some(Account(helpToSaveAccount, inAppPaymentsEnabled = config.inAppPaymentsEnabled, logger))
          case None =>
            logger.warn(s"$nino was enrolled according to help-to-save microservice but no account was found in NS&I - data is inconsistent")
            None
        }

      case false =>
        EitherT.rightT[Future, ErrorInfo](Option.empty[Account])
    }.value

}
