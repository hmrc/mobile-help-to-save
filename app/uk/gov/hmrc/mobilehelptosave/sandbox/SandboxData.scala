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

package uk.gov.hmrc.mobilehelptosave.sandbox

import java.time.temporal.TemporalAdjusters
import java.time.{LocalDate, YearMonth}
import play.api.LoggerLike
import uk.gov.hmrc.mobilehelptosave.config.SandboxDataConfig
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveAccount, HelpToSaveBonusTerm}
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.services.Clock

case class SandboxData(
  logger: LoggerLike,
  clock:  Clock,
  config: SandboxDataConfig) {

  private def today:             LocalDate = clock.now().toLocalDate
  private def openedDate:        LocalDate = today.minusMonths(17)
  private def endOfMonth:        LocalDate = today.`with`(TemporalAdjusters.lastDayOfMonth())
  private def startOfFirstTerm:  LocalDate = openedDate.`with`(TemporalAdjusters.firstDayOfMonth())
  private def endOfFirstTerm:    LocalDate = startOfFirstTerm.plusYears(2).minusDays(1)
  private def startOfSecondTerm: LocalDate = startOfFirstTerm.plusYears(2)
  private def endOfSecondTerm:   LocalDate = startOfSecondTerm.plusYears(2).minusDays(1)

  val account: Account = {
    Account(
      HelpToSaveAccount(
        accountNumber          = "1100000112057",
        openedYearMonth        = YearMonth.of(openedDate.getYear, openedDate.getMonthValue),
        isClosed               = false,
        blocked                = HtsBlocking(payments = false, withdrawals = false, bonuses = false),
        balance                = BigDecimal("100.00"),
        paidInThisMonth        = BigDecimal("30.00"),
        canPayInThisMonth      = BigDecimal("20.00"),
        maximumPaidInThisMonth = BigDecimal("50.00"),
        thisMonthEndDate       = endOfMonth,
        accountHolderForename  = "Testfore",
        accountHolderSurname   = "Testsur",
        accountHolderEmail     = Some("testemail@example.com"),
        bonusTerms = List(
          HelpToSaveBonusTerm(BigDecimal("125.00"), BigDecimal("0.00"), endOfFirstTerm, endOfFirstTerm.plusDays(1)),
          HelpToSaveBonusTerm(BigDecimal("0.00"), BigDecimal("0.00"), endOfSecondTerm, endOfSecondTerm.plusDays(1))
        ),
        closureDate      = None,
        closingBalance   = None,
        nbaAccountNumber = None,
        nbaPayee         = None,
        nbaRollNumber    = None,
        nbaSortCode      = None
      ),
      inAppPaymentsEnabled = config.inAppPaymentsEnabled,
      savingsGoalsEnabled  = true,
      logger,
      LocalDate.of(2018, 4, 30),
      savingsGoal = Some(SavingsGoal(goalAmount = Some(25.0), goalName = Some("\uD83C\uDFE1 New home")))
    )
  }

  val accountWithPotentialBonus = account.copy(potentialBonus = Some(225))

  val transactions = Transactions({
    Seq(
      (0L, 30.00, 130.00),
      (1L, 50.00, 100.00),
      (2L, -25.00, 50.00),
      (2L, 25.00, 75.00),
      (3L, 50.00, 50.00),
      (4L, -25.00, 00.00),
      (4L, 25.00, 25.00),
      (5L, -250.00, 00.00),
      (6L, 25.00, 250.00),
      (7L, 25.00, 225.00),
      (8L, 25.00, 200.00),
      (9L, 25.00, 175.00),
      (10L, 25.00, 150.00),
      (11L, 25.00, 125.00),
      (12L, 25.00, 100.00),
      (13L, 25.00, 75.00),
      (14L, 25.00, 50.00),
      (15L, 25.00, 25.00),

    ) map {
      case (monthsAgo, creditAmount, balance) =>
        val date = today.minusMonths(monthsAgo)
        Transaction(Credit, BigDecimal(creditAmount), date, date, BigDecimal(balance))
    }
  })

  val milestones = Milestones(
    List[MongoMilestone]()
  )

  val savingsUpdate = SavingsUpdateResponse(
    reportStartDate        = today.minusMonths(5).`with`(TemporalAdjusters.firstDayOfMonth()),
    reportEndDate          = today.minusMonths(1).`with`(TemporalAdjusters.lastDayOfMonth()),
    accountOpenedYearMonth = YearMonth.of(openedDate.getYear, openedDate.getMonthValue),
    savingsUpdate = Some(
      SavingsUpdate(
        savedInPeriod = Some(BigDecimal(100)),
        savedByMonth  = Some(SavedByMonth(numberOfMonths = 5, monthsSaved = 4)),
        goalsReached = Some(
          GoalsReached(currentAmount = 50, currentGoalName = Some("\uD83C\uDFE1 New home"), numberOfTimesReached = 2)
        ),
        amountEarnedTowardsBonus = Some(BigDecimal(50))
      )
    ),
    bonusUpdate = BonusUpdate(
      currentBonusTerm            = CurrentBonusTerm.First,
      monthsUntilBonus            = 8,
      currentBonus                = Some(BigDecimal(125)),
      highestBalance              = Some(BigDecimal(250)),
      potentialBonusAtCurrentRate = Some(BigDecimal(125)),
      potentialBonusWithFiveMore  = Some(BigDecimal(140)),
      maxBonus                    = Some(BigDecimal(225))
    )
  )

}
