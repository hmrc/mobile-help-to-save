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

import uk.gov.hmrc.mobilehelptosave.domain.{Account, BonusUpdate, Credit, CurrentBonusTerm, ErrorInfo, GoalsReached, SavedByMonth, SavingsGoal, SavingsUpdate, SavingsUpdateResponse, Transaction, Transactions}
import uk.gov.hmrc.mobilehelptosave.repository.SavingsGoalSetEvent

import java.time.temporal.ChronoUnit.MONTHS
import java.time.{LocalDate, LocalDateTime, Month, YearMonth, ZoneOffset}
import java.time.temporal.TemporalAdjusters
import scala.annotation.tailrec

trait SavingsUpdateService {
  type Result[T] = Either[ErrorInfo, T]

  def getSavingsUpdateResponse(
    account:      Account,
    transactions: Transactions,
    goalEvents:   List[SavingsGoalSetEvent]
  ): SavingsUpdateResponse
}

class HtsSavingsUpdateService extends SavingsUpdateService {

  def getSavingsUpdateResponse(
    account:      Account,
    transactions: Transactions,
    goalEvents:   List[SavingsGoalSetEvent]
  ): SavingsUpdateResponse = {
    val reportStartDate = calculateReportStartDate(account.openedYearMonth)
    val reportEndDate   = LocalDate.now().minusMonths(1).`with`(TemporalAdjusters.lastDayOfMonth())

    val reportTransactions: Seq[Transaction] = transactions.transactions.filter(transaction =>
      transaction.transactionDate.isAfter(reportStartDate.minusDays(1)) && transaction.transactionDate.isBefore(
        reportEndDate.plusDays(1)
      )
    )

    SavingsUpdateResponse(
      reportStartDate,
      reportEndDate,
      account.openedYearMonth,
      getSavingsUpdate(account, reportTransactions, goalEvents, reportStartDate, reportEndDate),
      getBonusUpdate(account)
    )
  }

  @tailrec
  private def calculateReportStartDate(accountStartDate: YearMonth): LocalDate =
    if (MONTHS.between(accountStartDate, YearMonth.now()) > 12)
      calculateReportStartDate(accountStartDate.plusYears(1))
    else
      LocalDate.of(accountStartDate.getYear, accountStartDate.getMonth, 1)

  private def getSavingsUpdate(
    account:         Account,
    transactions:    Seq[Transaction],
    goalEvents:      List[SavingsGoalSetEvent],
    reportStartDate: LocalDate,
    reportEndDate:   LocalDate
  ): Option[SavingsUpdate] =
    if (transactions.isEmpty) None
    else
      Some(
        SavingsUpdate(
          calculateTotalSaved(transactions),
          getMonthsSaved(transactions, MONTHS.between(YearMonth.from(reportStartDate), YearMonth.from(reportEndDate)).toInt + 1),
          calculateGoalsReached(account.savingsGoal, goalEvents, transactions, reportStartDate, reportEndDate),
          None
        )
      )

  private def getBonusUpdate(account: Account): BonusUpdate =
    BonusUpdate(account.currentBonusTerm,
                None,
                getCurrentBonus(account),
                defCalculateHighestBalance(account),
                None,
                None,
                None)

  private def calculateTotalSaved(transactions: Seq[Transaction]): Option[BigDecimal] = {
    val totalSaved = transactions.filter(t => t.operation == Credit).map(_.amount).sum
    if (totalSaved > BigDecimal(0)) Some(totalSaved) else None
  }

  private def getMonthsSaved(
    transactions: Seq[Transaction],
    totalMonths:  Int
  ): Option[SavedByMonth] = {
    val creditTransactionsByMonth = groupTransactionsByMonth(transactions)
    if (creditTransactionsByMonth.nonEmpty) Some(SavedByMonth(totalMonths, creditTransactionsByMonth.size)) else None
  }

  private def calculateGoalsReached(
    currentGoal:     Option[SavingsGoal],
    goalEvents:      List[SavingsGoalSetEvent],
    transactions:    Seq[Transaction],
    reportStartDate: LocalDate,
    reportEndDate:   LocalDate
  ): Option[GoalsReached] = {
    implicit def ord: Ordering[LocalDateTime] = Ordering.by(_.toInstant(ZoneOffset.UTC))
    if (currentGoal.isEmpty || currentGoal.get.goalAmount.isEmpty) None
    else {
      val currentGoalValue: Double                   = currentGoal.get.goalAmount.get
      val sortedEvents:     Seq[SavingsGoalSetEvent] = goalEvents.sortBy(_.date)
      val totalSavedEachMonth: Map[Month, BigDecimal] =
        groupTransactionsByMonth(transactions).map(t => Map(t._1 -> t._2.map(_.amount).sum)).flatten.toMap

      if (sortedEvents.last.date.isBefore(reportStartDate.atStartOfDay())) {
        val monthsWhereGoalWasMet: Map[Month, BigDecimal] = totalSavedEachMonth.filter(t => t._2 >= currentGoalValue)
        Some(GoalsReached(currentGoalValue, monthsWhereGoalWasMet.size))
      } else {
        val datesInRange: Seq[LocalDate] = reportStartDate.toEpochDay
          .until(reportEndDate.toEpochDay)
          .map(LocalDate.ofEpochDay)
          .filter(_.getDayOfMonth == 1)
          .sortBy(_.atStartOfDay())
        val lowestGoalEachMonth: Map[Month, Double] =
          sortedEvents
            .groupBy(_.date.getMonth)
            .map(e => Map(e._1 -> e._2.map(_.amount.getOrElse(50.0)).min))
            .flatten
            .toMap
        var currentGoal: Double =
          sortedEvents.filter(_.date.isBefore(reportStartDate.atStartOfDay())).last.amount.getOrElse(0)

        val numberOfTimesGoalHit = datesInRange
          .map { date =>
            currentGoal = lowestGoalEachMonth.getOrElse(date.getMonth, currentGoal)
            if (totalSavedEachMonth.getOrElse(date.getMonth, BigDecimal(0)).toDouble > currentGoal)
              Map(date.getMonth -> currentGoal)
            else None
          }
          .count(_.canEqual())

        Some(GoalsReached(currentGoalValue, numberOfTimesGoalHit))
      }
    }
  }

  private def getCurrentBonus(account: Account): Option[BigDecimal] =
    if (account.currentBonusTerm == CurrentBonusTerm.First) {
      account.bonusTerms.headOption.map(_.bonusEstimate)
    } else {
      account.bonusTerms.lastOption.map(_.bonusEstimate)
    }

  private def defCalculateHighestBalance(account: Account): Option[BigDecimal] = {
    val finalBonusTerms = account.bonusTerms.last
    if (account.currentBonusTerm == CurrentBonusTerm.First) {
      val highestBalance = finalBonusTerms.balanceMustBeMoreThanForBonus
      if (account.balance < highestBalance) Some(highestBalance) else None
    } else {
      val highestBalance = finalBonusTerms.balanceMustBeMoreThanForBonus + (finalBonusTerms.bonusEstimate * 2)
      if (account.balance < highestBalance) Some(highestBalance) else None
    }

  }

  private def groupTransactionsByMonth(transactions: Seq[Transaction]): Map[Month, Seq[Transaction]] =
    transactions.filter(t => t.operation == Credit).groupBy(i => i.transactionDate.getMonth)

}
