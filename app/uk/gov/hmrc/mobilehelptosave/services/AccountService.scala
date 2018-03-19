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

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}

import play.api.LoggerLike
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveProxyConnector
import uk.gov.hmrc.mobilehelptosave.domain.Account

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AccountServiceImpl])
trait AccountService {

  def account(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Account]]

  def getAmountPaidInThisMonth(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[BigDecimal]]

  def getRemainingCouldSaveThisMonth(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[BigDecimal]]

}

@Singleton
class AccountServiceImpl @Inject() (helpToSaveProxyConnector: HelpToSaveProxyConnector, logger: LoggerLike) extends AccountService {

  override def account(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Account]] =
    helpToSaveProxyConnector.nsiAccount(nino).map(_.map(nsiAccount => Account(nsiAccount.accountBalance, nsiAccount.investmentRemaining)))

  override def getAmountPaidInThisMonth(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[BigDecimal]] =
    account(nino).map{
      _ match {
        case Some(a) ⇒ Some(a.investmentRemaining)
        case None ⇒ None
      }
    }.recover {
      case e ⇒ logger.warn("Couldn't get amount paid in this month", e)
        None
    }

  override def getRemainingCouldSaveThisMonth(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[BigDecimal]] =
    getAmountPaidInThisMonth(nino).map{
      _ match {
        case Some(amount) ⇒ Some(50.00 - amount)
        case None ⇒ None
      }
    }

}
