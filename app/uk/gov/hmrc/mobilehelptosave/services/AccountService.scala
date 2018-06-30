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
import org.joda.time.YearMonth
import play.api.LoggerLike
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveConnectorGetAccount, HelpToSaveProxyConnector, NsiAccount, NsiBonusTerm}
import uk.gov.hmrc.mobilehelptosave.domain._

import scala.concurrent.{ExecutionContext, Future}

trait AccountService {

  def account(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Option[Account]]]

}

@Singleton
class HelpToSaveAccountService @Inject() (
  helpToSaveConnector: HelpToSaveConnectorGetAccount
) extends AccountService {

  override def account(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Option[Account]]] =
    EitherT(helpToSaveConnector.getAccount(nino))
      .map{maybeHtsAccount => maybeHtsAccount.map(Account.apply)}
      .value

}

@Singleton
class HelpToSaveProxyAccountService @Inject() (
  logger: LoggerLike,
  helpToSaveProxyConnector: HelpToSaveProxyConnector
) extends AccountService {

  override def account(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Option[Account]]] =
    helpToSaveProxyConnector.nsiAccount(nino).map(_.flatMap(nsiAccount => nsiAccountToAccount(nino, nsiAccount).map(Some.apply)))

  private def nsiAccountToAccount(nino: Nino, nsiAccount: NsiAccount): Either[ErrorInfo, Account] = {
    val paidInThisMonth = nsiAccount.currentInvestmentMonth.investmentLimit - nsiAccount.currentInvestmentMonth.investmentRemaining
    if (paidInThisMonth >= 0) {
      val sortedNsiTerms = nsiAccount.terms.sortBy(_.termNumber)
      sortedNsiTerms.headOption.fold[Either[ErrorInfo, Account]] {
        logger.warn(s"Account returned by NS&I for $nino contained no bonus terms")
        Left(ErrorInfo.General)
      } { firstNsiTerm =>
        val terms = sortedNsiTerms.map(nsiBonusTermToBonusTerm)
        Right(Account(
          number = nsiAccount.accountNumber,
          openedYearMonth = new YearMonth(firstNsiTerm.startDate),
          isClosed = nsiAccountClosedFlagToIsClosed(nsiAccount.accountClosedFlag),
          blocked = nsiAccountToBlocking(nsiAccount),
          balance = nsiAccount.accountBalance,
          paidInThisMonth = paidInThisMonth,
          canPayInThisMonth = nsiAccount.currentInvestmentMonth.investmentRemaining,
          maximumPaidInThisMonth = nsiAccount.currentInvestmentMonth.investmentLimit,
          thisMonthEndDate = nsiAccount.currentInvestmentMonth.endDate,
          bonusTerms = terms,
          closureDate = nsiAccount.accountClosureDate,
          closingBalance = nsiAccount.accountClosingBalance
        ))
      }
    } else {
      // investmentRemaining is unaffected by debits (only credits) so should never exceed investmentLimit
      logger.warn(
        s"investmentRemaining = ${nsiAccount.currentInvestmentMonth.investmentRemaining} and investmentLimit = ${nsiAccount.currentInvestmentMonth.investmentLimit} " +
        s"values returned by NS&I don't make sense because they imply a negative amount paid in this month"
      )
      Left(ErrorInfo.General)
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

  private def nsiAccountToBlocking(nsiAccount: NsiAccount): Blocking = Blocking(
    unspecified = nsiAccount.accountBlockingCode != "00" || nsiAccount.clientBlockingCode != "00"
  )

  private def nsiBonusTermToBonusTerm(nsiBonusTerm: NsiBonusTerm): BonusTerm = BonusTerm(
    bonusEstimate = nsiBonusTerm.bonusEstimate,
    bonusPaid = nsiBonusTerm.bonusPaid,
    endDate = nsiBonusTerm.endDate,
    bonusPaidOnOrAfterDate = nsiBonusTerm.endDate.plusDays(1)
  )

}
