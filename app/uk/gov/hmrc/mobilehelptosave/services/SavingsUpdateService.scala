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

  def getSavingsUpdateResponse(
    account:      Account,
    transactions: Transactions,
    goalEvents:   List[SavingsGoalSetEvent]
  ): SavingsUpdateResponse
}

class HtsSavingsUpdateService extends SavingsUpdateService {

  val reportEndDate: LocalDate = LocalDate.now().minusMonths(1).`with`(TemporalAdjusters.lastDayOfMonth())

  def getSavingsUpdateResponse(
    account:      Account,
    transactions: Transactions,
    goalEvents:   List[SavingsGoalSetEvent]
  ): SavingsUpdateResponse = {
    val reportStartDate = calculateReportStartDate(account.openedYearMonth)

    val reportTransactions: Seq[Transaction] = transactions.transactions.filter(transaction =>
      transaction.transactionDate.isAfter(reportStartDate.minusDays(1)) && transaction.transactionDate.isBefore(
        reportEndDate.plusDays(1)
      )
    )
    SavingsUpdateResponse(
      reportStartDate,
      reportEndDate,
      account.openedYearMonth,
      getSavingsUpdate(account, transactions, reportTransactions, goalEvents, reportStartDate),
      getBonusUpdate(account, reportTransactions, reportStartDate)
    )
  }

  private def getSavingsUpdate(
    account:            Account,
    transactions:       Transactions,
    reportTransactions: Seq[Transaction],
    goalEvents:         List[SavingsGoalSetEvent],
    reportStartDate:    LocalDate
  ): Option[SavingsUpdate] =
    if (reportTransactions.isEmpty) None
    else
      Some(
        SavingsUpdate(
          calculateTotalSaved(reportTransactions),
          getMonthsSaved(reportTransactions, reportStartDate),
          calculateGoalsReached(account.savingsGoal, goalEvents, reportTransactions, reportStartDate),
          calculateAmountEarnedTowardsBonus(transactions, reportTransactions, reportStartDate)
        )
      )

  private def getBonusUpdate(
    account:         Account,
    transactions:    Seq[Transaction],
    reportStartDate: LocalDate
  ): BonusUpdate =
    BonusUpdate(
      account.currentBonusTerm,
      getMonthsUntilNextBonus(account),
      getCurrentBonus(account),
      calculateHighestBalance(account),
      calculatePotentialBonusAtCurrentRate(transactions, reportStartDate, account),
      calculatePotentialBonusWithFiveMore(transactions, reportStartDate, account),
      calculateMaxBonus(account)
    )

  private def calculateTotalSaved(transactions: Seq[Transaction]): Option[BigDecimal] = {
    val totalSaved = transactions.filter(t => t.operation == Credit).map(_.amount).sum
    if (totalSaved > BigDecimal(0)) Some(totalSaved) else None
  }

  private def getMonthsSaved(
    transactions:    Seq[Transaction],
    reportStartDate: LocalDate
  ): Option[SavedByMonth] = {
    val creditTransactionsByMonth = groupTransactionsByMonth(transactions)
    val totalMonths               = MONTHS.between(YearMonth.from(reportStartDate), YearMonth.from(reportEndDate)).toInt + 1
    if (creditTransactionsByMonth.nonEmpty) Some(SavedByMonth(totalMonths, creditTransactionsByMonth.size)) else None
  }

  private def calculateGoalsReached(
    currentGoal:     Option[SavingsGoal],
    goalEvents:      List[SavingsGoalSetEvent],
    transactions:    Seq[Transaction],
    reportStartDate: LocalDate
  ): Option[GoalsReached] = {
    implicit def ord: Ordering[LocalDateTime] = Ordering.by(_.toInstant(ZoneOffset.UTC))
    if (currentGoal.isEmpty || currentGoal.get.goalAmount.isEmpty) None
    else {
      val currentGoalValue: Double                   = currentGoal.get.goalAmount.get
      val currentGoalName:  Option[String]           = currentGoal.get.goalName
      val sortedEvents:     Seq[SavingsGoalSetEvent] = goalEvents.sortBy(_.date)
      val totalSavedEachMonth: Map[Month, BigDecimal] =
        groupTransactionsByMonth(transactions).map(t => Map(t._1 -> t._2.map(_.amount).sum)).flatten.toMap

      if (sortedEvents.last.date.isBefore(reportStartDate.atStartOfDay())) {
        val monthsWhereGoalWasMet: Map[Month, BigDecimal] = totalSavedEachMonth.filter(t => t._2 >= currentGoalValue)
        Some(GoalsReached(currentGoalValue, currentGoalName, monthsWhereGoalWasMet.size))
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
        var currentGoal: Option[Double] =
          sortedEvents.filter(_.date.isBefore(reportStartDate.atStartOfDay())).lastOption.map(_.amount.getOrElse(50.0))

        val numberOfTimesGoalHit = datesInRange
          .map { date =>
            if (lowestGoalEachMonth.contains(date.getMonth)) {
              currentGoal = lowestGoalEachMonth.get(date.getMonth)
            }
            if (currentGoal.isDefined && totalSavedEachMonth
                  .getOrElse(date.getMonth, BigDecimal(0))
                  .toDouble >= currentGoal.getOrElse(50.0))
              Map(date.getMonth -> currentGoal.getOrElse(50.0))
            else None
          }
          .count(_.canEqual())

        if (numberOfTimesGoalHit > 0) Some(GoalsReached(currentGoalValue, currentGoalName, numberOfTimesGoalHit))
        else None
      }
    }
  }

  private def getMonthsUntilNextBonus(account: Account): Int =
    if (account.currentBonusTerm == CurrentBonusTerm.First) {
      MONTHS.between(YearMonth.now(), YearMonth.from(account.bonusTerms.head.endDate)).toInt + 1
    } else {
      MONTHS.between(YearMonth.now(), YearMonth.from(account.bonusTerms.last.endDate)).toInt + 1
    }

  private def calculateAmountEarnedTowardsBonus(
    transactions:       Transactions,
    reportTransactions: Seq[Transaction],
    reportStartDate:    LocalDate
  ): Option[BigDecimal] = {
    val transactionsBeforeReport: Seq[Transaction] = transactions.transactions.filter(transaction =>
      transaction.transactionDate.isBefore(
        reportStartDate
      )
    )
    val highestBalanceAtStartOfReport =
      if (transactionsBeforeReport.isEmpty) BigDecimal(0) else transactionsBeforeReport.map(_.balanceAfter).max
    val highestBalanceDuringReportingPeriod = reportTransactions.map(_.balanceAfter).max
    val amountEarned                        = (highestBalanceDuringReportingPeriod - highestBalanceAtStartOfReport) / 2
    if (amountEarned > 0) Some(amountEarned.setScale(2, BigDecimal.RoundingMode.HALF_UP)) else None
  }

  private def getCurrentBonus(account: Account): Option[BigDecimal] =
    if (account.currentBonusTerm == CurrentBonusTerm.First) {
      account.bonusTerms.headOption.map(_.bonusEstimate)
    } else {
      account.bonusTerms.lastOption.map(_.bonusEstimate)
    }

  private def calculateHighestBalance(account: Account): Option[BigDecimal] = {
    val finalBonusTerms = account.bonusTerms.last
    if (account.currentBonusTerm == CurrentBonusTerm.First) {
      val highestBalance = finalBonusTerms.balanceMustBeMoreThanForBonus
      if (account.balance < highestBalance) Some(highestBalance) else None
    } else {
      val highestBalance = finalBonusTerms.balanceMustBeMoreThanForBonus + (finalBonusTerms.bonusEstimate * 2)
      if (account.balance < highestBalance) Some(highestBalance.setScale(2, BigDecimal.RoundingMode.HALF_UP)) else None
    }
  }

  private def calculatePotentialBonusAtCurrentRate(
    transactions:    Seq[Transaction],
    reportStartDate: LocalDate,
    account:         Account
  ): Option[BigDecimal] =
    calculatePotentialBonus(calculateAverageSavingRate(transactions, reportStartDate), account)
      .map(BigDecimal(_).setScale(2, BigDecimal.RoundingMode.HALF_UP))

  private def calculatePotentialBonus(
    averageSavingsRate: Double,
    account:            Account
  ): Option[Double] =
    if (averageSavingsRate < 1) None
    else {
      val htsCalcService = new CalculatorService(account, averageSavingsRate)
      account.currentBonusTerm match {
        case CurrentBonusTerm.First =>
          Some(
            htsCalcService.getFirstBonusTermCalculation.getProjectedFirstBonus
          )
        case CurrentBonusTerm.Second =>
          Some(
            htsCalcService.getFinalBonusTermCalculation.getTotalProjectedBonuses
          )
        case _ => None
      }
    }

  private def calculatePotentialBonusWithFiveMore(
    transactions:    Seq[Transaction],
    reportStartDate: LocalDate,
    account:         Account
  ): Option[BigDecimal] = {
    val averageSavingsRate = calculateAverageSavingRate(transactions, reportStartDate)
    if (averageSavingsRate <= 45.0) {
      calculatePotentialBonus(averageSavingsRate + 5, account)
        .map(BigDecimal(_).setScale(2, BigDecimal.RoundingMode.HALF_UP))
    } else None
  }

  private def calculateMaxBonus(account: Account): Option[BigDecimal] =
    calculatePotentialBonus(50, account)
      .map(BigDecimal(_).setScale(2, BigDecimal.RoundingMode.HALF_UP))

  private def calculateAverageSavingRate(
    transactions:    Seq[Transaction],
    reportStartDate: LocalDate
  ): Double = {
    val totalMonths = MONTHS.between(YearMonth.from(reportStartDate), YearMonth.from(reportEndDate)).toInt + 1
    val totalSaved  = calculateTotalSaved(transactions)
    if (totalMonths == 0) 0 else (totalSaved.getOrElse(BigDecimal(0)) / totalMonths).toDouble
  }

  private def groupTransactionsByMonth(transactions: Seq[Transaction]): Map[Month, Seq[Transaction]] =
    transactions.filter(t => t.operation == Credit).groupBy(i => i.transactionDate.getMonth)

  @tailrec
  private def calculateReportStartDate(accountStartDate: YearMonth): LocalDate =
    if (MONTHS.between(accountStartDate, YearMonth.now()) > 12)
      calculateReportStartDate(accountStartDate.plusYears(1))
    else
      LocalDate.of(accountStartDate.getYear, accountStartDate.getMonth, 1)

}
