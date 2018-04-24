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
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveProxyConnector, NsiAccount, NsiBonusTerm}
import uk.gov.hmrc.mobilehelptosave.domain.{Account, BonusTerm}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AccountServiceImpl])
trait AccountService {

  def account(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Account]]

}

@Singleton
class AccountServiceImpl @Inject() (
  logger: LoggerLike,
  helpToSaveProxyConnector: HelpToSaveProxyConnector
) extends AccountService {

  override def account(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Account]] =
    helpToSaveProxyConnector.nsiAccount(nino).map(_.flatMap(nsiAccountToAccount))

  private def nsiAccountToAccount(nsiAccount: NsiAccount): Option[Account] = {
    val paidInThisMonth = nsiAccount.currentInvestmentMonth.investmentLimit - nsiAccount.currentInvestmentMonth.investmentRemaining
    if (paidInThisMonth >= 0) {
      Some(Account(
        isClosed = nsiAccountClosedFlagToIsClosed(nsiAccount.accountClosedFlag),
        balance = nsiAccount.accountBalance,
        paidInThisMonth = paidInThisMonth,
        canPayInThisMonth = nsiAccount.currentInvestmentMonth.investmentRemaining,
        maximumPaidInThisMonth = nsiAccount.currentInvestmentMonth.investmentLimit,
        thisMonthEndDate = nsiAccount.currentInvestmentMonth.endDate,
        bonusTerms = nsiAccount.terms.sortBy(_.termNumber).map(nsiBonusTermToBonusTerm),
        closureDate = nsiAccount.accountClosureDate,
        closingBalance = nsiAccount.accountClosingBalance
      ))
    } else {
      // investmentRemaining is unaffected by debits (only credits) so should never exceed investmentLimit
      logger.warn(
        s"investmentRemaining = ${nsiAccount.currentInvestmentMonth.investmentRemaining} and investmentLimit = ${nsiAccount.currentInvestmentMonth.investmentLimit} " +
        s"values returned by NS&I don't make sense because they imply a negative amount paid in this month"
      )
      None
    }
  }

  private def nsiAccountClosedFlagToIsClosed(accountClosedFlag: String): Boolean =
    if (accountClosedFlag == "C") {
      true
    } else if (accountClosedFlag.trim.isEmpty) {
      false
    } else {
      logger.warn(s"""Unknown value for accountClosedFlag: "$accountClosedFlag"""")
      false
    }

  private def nsiBonusTermToBonusTerm(nsiBonusTerm: NsiBonusTerm): BonusTerm = BonusTerm(
    bonusEstimate = nsiBonusTerm.bonusEstimate,
    bonusPaid = nsiBonusTerm.bonusPaid,
    bonusPaidOnOrAfterDate = nsiBonusTerm.endDate.plusDays(1)
  )

}
