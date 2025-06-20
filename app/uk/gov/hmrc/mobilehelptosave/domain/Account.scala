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

package uk.gov.hmrc.mobilehelptosave.domain

import java.time.temporal.ChronoUnit
import java.time.{LocalDate, YearMonth}
import play.api.LoggerLike
import play.api.libs.json.*
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveAccount, HelpToSaveBonusTerm}

case class BonusTerm(bonusEstimate: BigDecimal,
                     bonusPaid: BigDecimal,
                     endDate: LocalDate,
                     bonusPaidOnOrAfterDate: LocalDate,
                     bonusPaidByDate: LocalDate,
                     balanceMustBeMoreThanForBonus: BigDecimal
                    )

object BonusTerm {
  implicit val format: OFormat[BonusTerm] = Json.format[BonusTerm]
}

case class HtsBlocking(payments: Boolean, withdrawals: Boolean, bonuses: Boolean)

object HtsBlocking {
  implicit val format: OFormat[HtsBlocking] = Json.format[HtsBlocking]
}

case class Blocking(unspecified: Boolean = false, payments: Boolean, withdrawals: Boolean, bonuses: Boolean)

object Blocking {
  implicit val format: OFormat[Blocking] = Json.format[Blocking]
}

object CurrentBonusTerm extends Enumeration {
  val First, Second, AfterFinalTerm = Value

  implicit val reads: Reads[Value] = Reads.enumNameReads(CurrentBonusTerm)
  implicit val writes: Writes[Value] = Writes.enumNameWrites[CurrentBonusTerm.type]
}

case class Account(number: String,
                   openedYearMonth: YearMonth,
                   isClosed: Boolean,
                   blocked: Blocking,
                   balance: BigDecimal,
                   paidInThisMonth: BigDecimal,
                   canPayInThisMonth: BigDecimal,
                   maximumPaidInThisMonth: BigDecimal,
                   thisMonthEndDate: LocalDate,
                   nextPaymentMonthStartDate: Option[LocalDate],
                   accountHolderName: String,
                   accountHolderEmail: Option[String],
                   bonusTerms: Seq[BonusTerm],
                   currentBonusTerm: CurrentBonusTerm.Value,
                   closureDate: Option[LocalDate] = None,
                   closingBalance: Option[BigDecimal] = None,
                   nbaAccountNumber: Option[String],
                   nbaPayee: Option[String],
                   nbaRollNumber: Option[String],
                   nbaSortCode: Option[String],
                   inAppPaymentsEnabled: Boolean,
                   // This field is populated from the application config
                   savingsGoalsEnabled: Boolean = false,
                   // This field is populated from the mongo repository
                   savingsGoal: Option[SavingsGoal] = None,
                   daysRemainingInMonth: Long,
                   highestBalance: BigDecimal,
                   potentialBonus: Option[BigDecimal] = None
                  )

object Account {

  implicit val yearMonthFormat: Format[YearMonth] = uk.gov.hmrc.mobilehelptosave.json.Formats.YearMonthFormat
  implicit val format: OFormat[Account] = Json.format[Account]

  def apply(
    h: HelpToSaveAccount,
    inAppPaymentsEnabled: Boolean,
    savingsGoalsEnabled: Boolean,
    logger: LoggerLike,
    now: LocalDate,
    savingsGoal: Option[SavingsGoal]
  ): Account = Account(
    number                    = h.accountNumber,
    openedYearMonth           = h.openedYearMonth,
    isClosed                  = h.isClosed,
    blocked                   = Blocking(payments = h.blocked.payments, withdrawals = h.blocked.withdrawals, bonuses = h.blocked.bonuses),
    balance                   = h.balance,
    paidInThisMonth           = h.paidInThisMonth,
    canPayInThisMonth         = h.canPayInThisMonth,
    maximumPaidInThisMonth    = h.maximumPaidInThisMonth,
    thisMonthEndDate          = h.thisMonthEndDate,
    nextPaymentMonthStartDate = nextPaymentMonthStartDate(h),
    accountHolderName         = h.accountHolderForename + " " + h.accountHolderSurname,
    accountHolderEmail        = h.accountHolderEmail,
    bonusTerms                = bonusTerms(h, logger),
    currentBonusTerm          = currentBonusTerm(h),
    closureDate               = h.closureDate,
    closingBalance            = h.closingBalance,
    nbaAccountNumber          = h.nbaAccountNumber,
    nbaPayee                  = h.nbaPayee,
    nbaRollNumber             = h.nbaRollNumber,
    nbaSortCode               = h.nbaSortCode,
    inAppPaymentsEnabled      = inAppPaymentsEnabled,
    savingsGoalsEnabled       = savingsGoalsEnabled,
    daysRemainingInMonth      = calculateDaysRemainingInMonth(now, h),
    savingsGoal               = savingsGoal,
    highestBalance            = highestBalance(bonusTerms(h, logger), currentBonusTerm(h))
  )

  /** Calculate the number of days between the current date and the end of month date. We're doing this to supply that number to the apps as part of
    * the Account structure because the app devs want to vary the messages displayed based on how much time the user has left to make payments in the
    * month and they are unwilling to write this code themselves.
    *
    * The returned value will be 1-indexed, i.e. if the supplied date is today then the result will be "1 day left"
    *
    * @return
    *   \- calculated number of days between now and the end of month, plus one (so if the supplied date is today the result will be 1)
    */
  private def calculateDaysRemainingInMonth(
    now: LocalDate,
    h: HelpToSaveAccount
  ): Long =
    ChronoUnit.DAYS.between(now, h.thisMonthEndDate) + 1

  private def nextPaymentMonthStartDate(h: HelpToSaveAccount) =
    if (h.thisMonthEndDate.isBefore(h.bonusTerms.last.endDate)) {
      Some(h.thisMonthEndDate.plusDays(1))
    } else {
      None
    }

  private def currentBonusTerm(h: HelpToSaveAccount) =
    if (h.thisMonthEndDate.isAfter(h.bonusTerms(1).endDate)) {
      CurrentBonusTerm.AfterFinalTerm
    } else if (h.thisMonthEndDate.isAfter(h.bonusTerms.head.endDate)) {
      CurrentBonusTerm.Second
    } else {
      CurrentBonusTerm.First
    }

  private def bonusTerms(
    h: HelpToSaveAccount,
    logger: LoggerLike
  ): Seq[BonusTerm] = {

    def bonusTerm(
      htsTerm: HelpToSaveBonusTerm,
      balanceMustBeMoreThanForBonus: BigDecimal
    ) = BonusTerm(
      bonusEstimate                 = htsTerm.bonusEstimate,
      bonusPaid                     = htsTerm.bonusPaid,
      endDate                       = htsTerm.endDate,
      bonusPaidOnOrAfterDate        = htsTerm.bonusPaidOnOrAfterDate,
      bonusPaidByDate               = htsTerm.bonusPaidOnOrAfterDate,
      balanceMustBeMoreThanForBonus = balanceMustBeMoreThanForBonus
    )

    if (h.bonusTerms.size > 2) {
      logger.warn(
        s"Account contained ${h.bonusTerms.size} bonus terms, which is more than the expected 2 - discarding all but the first 2 terms"
      )
    }

    h.bonusTerms.take(2).foldLeft(Vector.empty[BonusTerm]) { (acc, htsTerm) =>
      val balanceMustBeMoreThanForBonus: BigDecimal =
        acc.lastOption.fold(BigDecimal(0))(prevHtsTerm => prevHtsTerm.bonusEstimate * 2)

      acc :+ bonusTerm(htsTerm, balanceMustBeMoreThanForBonus)
    }
  }

  private def highestBalance(
    bonusTerms: Seq[BonusTerm],
    currentBonusTerm: CurrentBonusTerm.Value
  ): BigDecimal = {
    val finalBonusTerms = bonusTerms.last
    if (currentBonusTerm == CurrentBonusTerm.First) {
      finalBonusTerms.balanceMustBeMoreThanForBonus
    } else {
      val highestBalance = finalBonusTerms.balanceMustBeMoreThanForBonus + (finalBonusTerms.bonusEstimate * 2)
      highestBalance.setScale(2, BigDecimal.RoundingMode.HALF_UP)
    }
  }
}
