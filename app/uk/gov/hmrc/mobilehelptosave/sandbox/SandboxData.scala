/*
 * Copyright 2020 HM Revenue & Customs
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

import java.net.URLDecoder
import java.time.temporal.TemporalAdjusters
import java.time.{LocalDate, YearMonth}

import play.api.LoggerLike
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.config.SandboxDataConfig
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveAccount, HelpToSaveBonusTerm}
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.services.Clock

case class SandboxData(
  logger: LoggerLike,
  clock:  Clock,
  config: SandboxDataConfig) {

  private def today:             LocalDate = clock.now().toLocalDate
  private def openedDate:        LocalDate = today.minusMonths(7)
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
        blocked                = Blocking(unspecified = false, payments = false, withdrawals = false, bonuses = false),
        balance                = BigDecimal("230.00"),
        paidInThisMonth        = BigDecimal("30.00"),
        canPayInThisMonth      = BigDecimal("20.00"),
        maximumPaidInThisMonth = BigDecimal("50.00"),
        thisMonthEndDate       = endOfMonth,
        accountHolderForename  = "Testfore",
        accountHolderSurname   = "Testsur",
        accountHolderEmail     = Some("testemail@example.com"),
        bonusTerms = List(
          HelpToSaveBonusTerm(BigDecimal("110.25"), BigDecimal("0.00"), endOfFirstTerm, endOfFirstTerm.plusDays(1)),
          HelpToSaveBonusTerm(BigDecimal("0.00"), BigDecimal("0.00"), endOfSecondTerm, endOfSecondTerm.plusDays(1))
        ),
        closureDate    = None,
        closingBalance = None
      ),
      inAppPaymentsEnabled = config.inAppPaymentsEnabled,
      savingsGoalsEnabled  = true,
      logger,
      LocalDate.of(2018, 4, 30),
      savingsGoal = Some(SavingsGoal(goalAmount = 25.0, goalName = Some("\\xF0\\x9F\\x8F\\xA1 New home")))
    )
  }

  val transactions = Transactions({
    Seq(
      (0L, 20.50, 220.50),
      (1L, 20.00, 200.00),
      (1L, 18.20, 180.00),
      (2L, 10.40, 161.80),
      (3L, 35.00, 151.40),
      (3L, 15.00, 116.40),
      (4L, 06.00, 101.40),
      (5L, 20.40, 95.40),
      (5L, 10.00, 75.00),
      (6L, 25.00, 65.00),
      (7L, 40.00, 40.00)
    ) map {
      case (monthsAgo, creditAmount, balance) =>
        val date = today.minusMonths(monthsAgo)
        Transaction(Credit, BigDecimal(creditAmount), date, date, BigDecimal(balance))
    }
  })

  val milestones = Milestones(
    List[MongoMilestone]()
  )

}
