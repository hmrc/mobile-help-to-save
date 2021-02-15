/*
 * Copyright 2021 HM Revenue & Customs
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

import uk.gov.hmrc.mobilehelptosave.domain.{Account, BonusUpdate, CurrentBonusTerm, Debit, ErrorInfo, SavingsUpdate, SavingsUpdateResponse, Transaction, Transactions}

import java.time.temporal.ChronoUnit.MONTHS
import java.time.{LocalDate, YearMonth}
import java.time.temporal.TemporalAdjusters
import scala.annotation.tailrec

trait SavingsUpdateService {
  type Result[T] = Either[ErrorInfo, T]

  def getSavingsUpdateResponse(
    account:      Account,
    transactions: Transactions
  ): SavingsUpdateResponse
}

class HtsSavingsUpdateService extends SavingsUpdateService {

  def getSavingsUpdateResponse(
    account:      Account,
    transactions: Transactions
  ): SavingsUpdateResponse = {
    val reportStartDate = calculateReportStartDate(account.openedYearMonth)
    val reportEndDate   = LocalDate.now().`with`(TemporalAdjusters.lastDayOfMonth())

    val reportTransactions: Seq[Transaction] = transactions.transactions.filter(transaction =>
      transaction.transactionDate.isAfter(reportStartDate.minusDays(1)) && transaction.transactionDate.isBefore(
        reportEndDate.plusDays(1)
      )
    )

    SavingsUpdateResponse(
      reportStartDate,
      reportEndDate,
      account.openedYearMonth,
      getSavingsUpdate(reportTransactions),
      getBonusUpdate(account)
    )
  }

  @tailrec
  private def calculateReportStartDate(accountStartDate: YearMonth): LocalDate =
    if (MONTHS.between(accountStartDate, YearMonth.now()) > 12)
      calculateReportStartDate(accountStartDate.plusYears(1))
    else
      LocalDate.of(accountStartDate.getYear, accountStartDate.getMonth, 1)

  private def getSavingsUpdate(transactions: Seq[Transaction]): Option[SavingsUpdate] =
    if (transactions.isEmpty) None
    else Some(SavingsUpdate(calculateTotalSaved(transactions), None, None, None))

  private def getBonusUpdate(account: Account): BonusUpdate =
    BonusUpdate(account.currentBonusTerm, None, getCurrentBonus(account), None, None, None, None)

  private def calculateTotalSaved(transactions: Seq[Transaction]): Option[BigDecimal] = {
    val totalSaved = transactions.filter(t => t.operation == Debit).map(_.amount).sum
    if (totalSaved > BigDecimal(0)) Some(totalSaved) else None
  }

  private def getCurrentBonus(account: Account): Option[BigDecimal] =
    if (account.currentBonusTerm == CurrentBonusTerm.First) {
      account.bonusTerms.headOption.map(_.bonusEstimate)
    } else {
      account.bonusTerms.lastOption.map(_.bonusEstimate)
    }

}
