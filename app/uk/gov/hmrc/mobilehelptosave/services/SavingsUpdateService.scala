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

import uk.gov.hmrc.helptosavecalculator.Calculator
import uk.gov.hmrc.helptosavecalculator.models.CalculatorResponse
import uk.gov.hmrc.mobilehelptosave.domain.{Account, BonusUpdate, CurrentBonusTerm, ErrorInfo, GoalsReached, SavingsUpdate, SavingsUpdateResponse, Transaction, Transactions}

import java.time.temporal.ChronoUnit.MONTHS
import java.time.{LocalDate, YearMonth}
import java.time.temporal.TemporalAdjusters
import scala.annotation.tailrec

trait SavingsUpdateService {
  type Result[T] = Either[ErrorInfo, T]

  def getHTSTaxKalcResults(
    account:      Account,
    transactions: Transactions
  ): SavingsUpdateResponse
}

class HtsSavingsUpdateService extends SavingsUpdateService {

  def getHTSTaxKalcResults(
    account:      Account,
    transactions: Transactions
  ): SavingsUpdateResponse = {
    //val kalc: CalculatorResponse = Calculator.INSTANCE.run(10.0)
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
      getSavingsUpdate(reportTransactions),
      getBonusUpdate
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
    else Some(SavingsUpdate(Some(100.00), Some(4), Some(GoalsReached(25.00, 2)), Some(50.00)))

  private def getBonusUpdate: BonusUpdate =
    BonusUpdate(CurrentBonusTerm.First, Some(8), Some(75.00), Some(200.00), Some(300.00), Some(500.00), Some(600.00))

}
