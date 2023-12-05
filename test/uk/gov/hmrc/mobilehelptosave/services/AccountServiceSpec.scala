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

package uk.gov.hmrc.mobilehelptosave.services

import cats.MonadError
import cats.syntax.applicativeError._
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.OneInstancePerTest
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.{AccountTestData, TransactionTestData}
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveAccount, HelpToSaveBonusTerm, HelpToSaveEnrolmentStatus, HelpToSaveGetAccount, HelpToSaveGetTransactions}
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.repository.{SavingsGoalEvent, SavingsGoalEventRepo, SavingsGoalSetEvent}
import uk.gov.hmrc.mobilehelptosave.support.{LoggerStub, TestF}

import java.math.MathContext
import java.time.{LocalDate, LocalDateTime, YearMonth}
import scala.concurrent.{ExecutionContext, Future}

class AccountServiceSpec
    extends AnyWordSpecLike
    with Matchers
    with AccountTestData
    with MockFactory
    with OneInstancePerTest
    with LoggerStub
    with TestF
    with TransactionTestData {

  private val generator  = new Generator(0)
  private val nino       = generator.nextNino
  private val testConfig = TestAccountServiceConfig(inAppPaymentsEnabled = false, savingsGoalsEnabled = false)

  private val testMilestonesConfig =
    TestMilestonesConfig(balanceMilestoneCheckEnabled      = true,
                         bonusPeriodMilestoneCheckEnabled  = true,
                         bonusReachedMilestoneCheckEnabled = true)

  private implicit val passedHc: HeaderCarrier = HeaderCarrier()

  "account" should {
    "convert the account from the help-to-save domain to the mobile-help-to-save domain" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val fakeGetTransactions  = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))

      val service =
        new HtsAccountService[TestF](logger,
                                     testConfig,
                                     fakeEnrolmentStatus,
                                     fakeGetAccount,
                                     savingsGoalEventRepo,
                                     fakeBalanceMilestoneService,
                                     fakeBonusPeriodMilestoneService,
                                     fakeBonusReachedMilestoneService,
                                     fakeMongoUpdateService,
                                     new HtsSavingsUpdateService,
                                     fakeGetTransactions,
                                     testMilestonesConfig)

      // Because the service uses the system time to calculate the number of remaining days we need to adjust that in the result
      val result = service.account(nino).unsafeGet.map(_.map(_.copy(daysRemainingInMonth = 1)))
      result shouldBe Right(Some(mobileHelpToSaveAccount.copy(savingsGoalsEnabled = testConfig.savingsGoalsEnabled)))
    }

    "fold the value of the 'savingsGoalEnabled' config into the returned account" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val fakeGetTransactions  = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))

      val config = testConfig.copy(savingsGoalsEnabled = true)
      val service =
        new HtsAccountService[TestF](logger,
                                     config,
                                     fakeEnrolmentStatus,
                                     fakeGetAccount,
                                     savingsGoalEventRepo,
                                     fakeBalanceMilestoneService,
                                     fakeBonusPeriodMilestoneService,
                                     fakeBonusReachedMilestoneService,
                                     fakeMongoUpdateService,
                                     new HtsSavingsUpdateService,
                                     fakeGetTransactions,
                                     testMilestonesConfig)

      // Because the service uses the system time to calculate the number of remaining days we need to adjust that in the result
      val result = service.account(nino).unsafeGet.map(_.map(_.copy(daysRemainingInMonth = 1)))
      result shouldBe Right(Some(mobileHelpToSaveAccount.copy(savingsGoalsEnabled = true)))

    }

    "allow inAppPaymentsEnabled to be overridden with configuration" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val fakeGetTransactions  = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))
      val service =
        new HtsAccountService[TestF](logger,
                                     testConfig.copy(inAppPaymentsEnabled = true),
                                     fakeEnrolmentStatus,
                                     fakeGetAccount,
                                     savingsGoalEventRepo,
                                     fakeBalanceMilestoneService,
                                     fakeBonusPeriodMilestoneService,
                                     fakeBonusReachedMilestoneService,
                                     fakeMongoUpdateService,
                                     new HtsSavingsUpdateService,
                                     fakeGetTransactions,
                                     testMilestonesConfig)

      // Because the service uses the system time to calculate the number of remaining days we need to adjust that in the result
      val result = service.account(nino).unsafeGet.map(_.map(_.copy(daysRemainingInMonth = 1)))
      result shouldBe Right(
        Some(
          mobileHelpToSaveAccount
            .copy(inAppPaymentsEnabled = true, savingsGoalsEnabled = testConfig.savingsGoalsEnabled)
        )
      )
    }

    "not return a potential bonus is the account is less than 3 months old" in {
      val fakeEnrolmentStatus = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount =
        fakeHelpToSaveGetAccount(nino,
                                 Right(Some(helpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(2)))))
      val fakeGetTransactions  = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))

      val service =
        new HtsAccountService[TestF](logger,
                                     testConfig,
                                     fakeEnrolmentStatus,
                                     fakeGetAccount,
                                     savingsGoalEventRepo,
                                     fakeBalanceMilestoneService,
                                     fakeBonusPeriodMilestoneService,
                                     fakeBonusReachedMilestoneService,
                                     fakeMongoUpdateService,
                                     new HtsSavingsUpdateService,
                                     fakeGetTransactions,
                                     testMilestonesConfig)

      // Because the service uses the system time to calculate the number of remaining days we need to adjust that in the result
      val result = service.account(nino).unsafeGet.map(_.map(_.copy(daysRemainingInMonth = 1)))
      result shouldBe Right(
        Some(
          mobileHelpToSaveAccount.copy(openedYearMonth     = YearMonth.now().minusMonths(2),
                                       savingsGoalsEnabled = testConfig.savingsGoalsEnabled,
                                       potentialBonus      = None)
        )
      )
    }

    "Return a potential bonus of 0 if the max potential bonus is calculated as 0" in {
      val bonusTerms1         = helpToSaveAccount.bonusTerms.head
      val bonusTerms2         = helpToSaveAccount.bonusTerms.last
      val fakeEnrolmentStatus = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount =
        fakeHelpToSaveGetAccount(
          nino,
          Right(
            Some(
              helpToSaveAccount.copy(
                balance           = BigDecimal("0"),
                paidInThisMonth   = BigDecimal("50"),
                canPayInThisMonth = BigDecimal("0"),
                thisMonthEndDate  = LocalDate.of(YearMonth.now().getYear, 6, 28),
                bonusTerms = Seq(bonusTerms1.copy(bonusEstimate = BigDecimal("600"), bonusPaid = BigDecimal("600")),
                                 bonusTerms2.copy(bonusEstimate = BigDecimal("0")))
              )
            )
          )
        )
      val fakeGetTransactions  = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))

      val service =
        new HtsAccountService[TestF](logger,
                                     testConfig,
                                     fakeEnrolmentStatus,
                                     fakeGetAccount,
                                     savingsGoalEventRepo,
                                     fakeBalanceMilestoneService,
                                     fakeBonusPeriodMilestoneService,
                                     fakeBonusReachedMilestoneService,
                                     fakeMongoUpdateService,
                                     new HtsSavingsUpdateService,
                                     fakeGetTransactions,
                                     testMilestonesConfig)

      // Because the service uses the system time to calculate the number of remaining days we need to adjust that in the result
      val result = service.account(nino).unsafeGet.map(_.map(_.copy(daysRemainingInMonth = 1)))
      result.map(_.map(_.potentialBonus)) shouldBe Right(Some(Some(BigDecimal("0"))))
    }

    "Return a user's potential bonus as their current estimate if they have not saved recently" in {
      val fakeEnrolmentStatus = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount =
        fakeHelpToSaveGetAccount(
          nino,
          Right(
            Some(
              helpToSaveAccount.copy(
                thisMonthEndDate = LocalDate.of(YearMonth.now().getYear, 1, 31)
              )
            )
          )
        )
      val fakeGetTransactions =
        fakeHelpToSaveGetTransactions(Right(transactionsWithAverageSavingsRate(BigDecimal("0"))))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))

      val service =
        new HtsAccountService[TestF](logger,
                                     testConfig,
                                     fakeEnrolmentStatus,
                                     fakeGetAccount,
                                     savingsGoalEventRepo,
                                     fakeBalanceMilestoneService,
                                     fakeBonusPeriodMilestoneService,
                                     fakeBonusReachedMilestoneService,
                                     fakeMongoUpdateService,
                                     new HtsSavingsUpdateService,
                                     fakeGetTransactions,
                                     testMilestonesConfig)

      // Because the service uses the system time to calculate the number of remaining days we need to adjust that in the result
      val result = service.account(nino).unsafeGet.map(_.map(_.copy(daysRemainingInMonth = 1)))
      result.map(_.map(_.potentialBonus)) shouldBe Right(Some(Some(BigDecimal("12"))))
    }

    "Return a user's potential bonus using an average rate based on the previous year if they are in the first month of the 2nd year" in {
      val fakeEnrolmentStatus = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val bonusTerms          = helpToSaveAccount.bonusTerms
      val fakeGetAccount =
        fakeHelpToSaveGetAccount(
          nino,
          Right(
            Some(
              helpToSaveAccount.copy(
                balance           = BigDecimal("650"),
                openedYearMonth   = YearMonth.now().minusYears(1),
                paidInThisMonth   = BigDecimal("50"),
                canPayInThisMonth = BigDecimal("0"),
                bonusTerms = Seq(
                  HelpToSaveBonusTerm(
                    bonusEstimate = BigDecimal("300"),
                    bonusPaid     = 0,
                    endDate =
                      LocalDate.of(YearMonth.now().plusMonths(11).getYear, YearMonth.now().plusMonths(11).getMonth, 28),
                    bonusPaidOnOrAfterDate =
                      LocalDate.of(YearMonth.now().plusYears(1).getYear, YearMonth.now().plusYears(1).getMonth, 1)
                  ),
                  bonusTerms.last
                ),
                thisMonthEndDate = LocalDate.of(YearMonth.now().getYear, YearMonth.now().getMonth, 28)
              )
            )
          )
        )
      val fakeGetTransactions =
        fakeHelpToSaveGetTransactions(Right(transactionsWithAverageSavingsRate(BigDecimal("50"))))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))

      val service =
        new HtsAccountService[TestF](logger,
                                     testConfig,
                                     fakeEnrolmentStatus,
                                     fakeGetAccount,
                                     savingsGoalEventRepo,
                                     fakeBalanceMilestoneService,
                                     fakeBonusPeriodMilestoneService,
                                     fakeBonusReachedMilestoneService,
                                     fakeMongoUpdateService,
                                     new HtsSavingsUpdateService,
                                     fakeGetTransactions,
                                     testMilestonesConfig)

      // Because the service uses the system time to calculate the number of remaining days we need to adjust that in the result
      val result = service.account(nino).unsafeGet.map(_.map(_.copy(daysRemainingInMonth = 1)))
      result.map(_.map(_.potentialBonus)) shouldBe Right(Some(Some(BigDecimal("600"))))
    }

    // this is to avoid unnecessary load on NS&I, see NGC-3799
    "return None without attempting to get account from help-to-save when the user is not enrolled" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(false))
      val fakeGetTransactions  = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))
      val service =
        new HtsAccountService[TestF](logger,
                                     testConfig,
                                     fakeEnrolmentStatus,
                                     ShouldNotBeCalledGetAccount,
                                     savingsGoalEventRepo,
                                     fakeBalanceMilestoneService,
                                     fakeBonusPeriodMilestoneService,
                                     fakeBonusReachedMilestoneService,
                                     fakeMongoUpdateService,
                                     new HtsSavingsUpdateService,
                                     fakeGetTransactions,
                                     testMilestonesConfig)
      service.account(nino).unsafeGet shouldBe Right(None)

      (slf4jLoggerStub.warn(_: String)) verify * never
    }

    "return None and log a warning when user is enrolled according to help-to-save but no account exists in NS&I" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(None))
      val fakeGetTransactions  = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))
      val service =
        new HtsAccountService[TestF](logger,
                                     testConfig,
                                     fakeEnrolmentStatus,
                                     fakeGetAccount,
                                     savingsGoalEventRepo,
                                     fakeBalanceMilestoneService,
                                     fakeBonusPeriodMilestoneService,
                                     fakeBonusReachedMilestoneService,
                                     fakeMongoUpdateService,
                                     new HtsSavingsUpdateService,
                                     fakeGetTransactions,
                                     testMilestonesConfig)

      service.account(nino).unsafeGet shouldBe Right(None)

      (slf4jLoggerStub
        .warn(_: String)) verify s"${nino.value} was enrolled according to help-to-save microservice but no account was found in NS&I - data is inconsistent"
    }

    "not call either fetchSavingsGoal or fetchNSAndIAccount if the user isn't enrolled" in {
      val fakeEnrolmentStatus = fakeHelpToSaveEnrolmentStatus(nino, Right(false))

      val fakeGetAccount = new HelpToSaveGetAccount[TestF] {
        override def getAccount(
          nino:        Nino
        )(implicit hc: HeaderCarrier
        ): TestF[Either[ErrorInfo, Option[HelpToSaveAccount]]] =
          fail("getAccount should not have been called")
      }
      val fakeGetTransactions = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))

      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))

      val service =
        new HtsAccountService[TestF](logger,
                                     testConfig,
                                     fakeEnrolmentStatus,
                                     fakeGetAccount,
                                     savingsGoalEventRepo,
                                     fakeBalanceMilestoneService,
                                     fakeBonusPeriodMilestoneService,
                                     fakeBonusReachedMilestoneService,
                                     fakeMongoUpdateService,
                                     new HtsSavingsUpdateService,
                                     fakeGetTransactions,
                                     testMilestonesConfig)

      service.account(nino).unsafeGet shouldBe Right(None)
    }

    "return errors returned by connector.enrolmentStatus" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Left(ErrorInfo.General))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val fakeGetTransactions  = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))
      val service =
        new HtsAccountService[TestF](logger,
                                     testConfig,
                                     fakeEnrolmentStatus,
                                     fakeGetAccount,
                                     savingsGoalEventRepo,
                                     fakeBalanceMilestoneService,
                                     fakeBonusPeriodMilestoneService,
                                     fakeBonusReachedMilestoneService,
                                     fakeMongoUpdateService,
                                     new HtsSavingsUpdateService,
                                     fakeGetTransactions,
                                     testMilestonesConfig)
      service.account(nino).unsafeGet shouldBe Left(ErrorInfo.General)
    }

    "return errors returned by connector.getAccount" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Left(ErrorInfo.General))
      val fakeGetTransactions  = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))
      val service =
        new HtsAccountService[TestF](logger,
                                     testConfig,
                                     fakeEnrolmentStatus,
                                     fakeGetAccount,
                                     savingsGoalEventRepo,
                                     fakeBalanceMilestoneService,
                                     fakeBonusPeriodMilestoneService,
                                     fakeBonusReachedMilestoneService,
                                     fakeMongoUpdateService,
                                     new HtsSavingsUpdateService,
                                     fakeGetTransactions,
                                     testMilestonesConfig)
      service.account(nino).unsafeGet shouldBe Left(ErrorInfo.General)
    }

    "return errors if savingsGoalRepo.get throws exception" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val fakeGetTransactions  = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Left(new Exception("test exception")))
      val service =
        new HtsAccountService[TestF](logger,
                                     testConfig,
                                     fakeEnrolmentStatus,
                                     fakeGetAccount,
                                     savingsGoalEventRepo,
                                     fakeBalanceMilestoneService,
                                     fakeBonusPeriodMilestoneService,
                                     fakeBonusReachedMilestoneService,
                                     fakeMongoUpdateService,
                                     new HtsSavingsUpdateService,
                                     fakeGetTransactions,
                                     testMilestonesConfig)
      service.account(nino).unsafeGet shouldBe Left(ErrorInfo.General)
    }
  }

  private def fakeBalanceMilestoneService: BalanceMilestonesService[TestF] =
    new BalanceMilestonesService[TestF] {

      override def balanceMilestoneCheck(
        nino:                        Nino,
        currentBalance:              BigDecimal,
        secondPeriodBonusPaidByDate: LocalDate
      )(implicit hc:                 HeaderCarrier
      ): TestF[MilestoneCheckResult] =
        F.pure(CouldNotCheck)

    }

  private def fakeBonusPeriodMilestoneService: BonusPeriodMilestonesService[TestF] =
    new BonusPeriodMilestonesService[TestF] {

      override def bonusPeriodMilestoneCheck(
        nino:             Nino,
        bonusTerms:       Seq[BonusTerm],
        currentBalance:   BigDecimal,
        currentBonusTerm: CurrentBonusTerm.Value,
        accountClosed:    Boolean
      )(implicit hc:      HeaderCarrier
      ): TestF[MilestoneCheckResult] = F.pure(CouldNotCheck)
    }

  private def fakeBonusReachedMilestoneService: BonusReachedMilestonesService[TestF] =
    new BonusReachedMilestonesService[TestF] {

      override def bonusReachedMilestoneCheck(
        nino:             Nino,
        bonusTerms:       Seq[BonusTerm],
        currentBonusTerm: CurrentBonusTerm.Value
      )(implicit hc:      HeaderCarrier
      ): TestF[MilestoneCheckResult] = F.pure(CouldNotCheck)
    }

  private def fakeHelpToSaveEnrolmentStatus(
    expectedNino:    Nino,
    enrolledOrError: Either[ErrorInfo, Boolean]
  ): HelpToSaveEnrolmentStatus[TestF] =
    new HelpToSaveEnrolmentStatus[TestF] {

      override def enrolmentStatus()(implicit hc: HeaderCarrier): TestF[Either[ErrorInfo, Boolean]] = {
        nino shouldBe expectedNino
        hc   shouldBe passedHc

        F.pure(enrolledOrError)
      }
    }

  private def fakeHelpToSaveGetAccount(
    expectedNino:   Nino,
    accountOrError: Either[ErrorInfo, Option[HelpToSaveAccount]]
  ): HelpToSaveGetAccount[TestF] =
    new HelpToSaveGetAccount[TestF] {

      override def getAccount(
        nino:        Nino
      )(implicit hc: HeaderCarrier
      ): TestF[Either[ErrorInfo, Option[HelpToSaveAccount]]] = {
        nino shouldBe expectedNino
        hc   shouldBe passedHc

        F.pure(accountOrError)
      }
    }

  private def fakeHelpToSaveGetTransactions(
    transactionsOrError: Either[ErrorInfo, Transactions]
  ): HelpToSaveGetTransactions[TestF] =
    new HelpToSaveGetTransactions[TestF] {

      override def getTransactions(nino: Nino)(implicit hc: HeaderCarrier): TestF[Either[ErrorInfo, Transactions]] =
        F.pure(transactionsOrError)
    }

  private def fakeSavingsGoalEventsRepo(
    expectedNino:     Nino,
    goalsOrException: Either[Throwable, List[SavingsGoalEvent]]
  ): SavingsGoalEventRepo[TestF] =
    new SavingsGoalEventRepo[TestF] {

      override def setGoal(
        nino:                        Nino,
        amount:                      Option[Double] = None,
        name:                        Option[String] = None,
        secondPeriodBonusPaidByDate: LocalDate
      ): TestF[Unit] = {
        nino shouldBe expectedNino
        F.unit
      }

      override def getEvents(nino: Nino): TestF[List[SavingsGoalEvent]] = {
        nino shouldBe expectedNino
        goalsOrException match {
          case Left(t)     => F.raiseError(t)
          case Right(goal) => F.pure(goal)
        }
      }

      override def deleteGoal(
        nino:                        Nino,
        secondPeriodBonusPaidByDate: LocalDate
      ): TestF[Unit] = {
        nino shouldBe expectedNino
        F.unit
      }

      override def clearGoalEvents(): TestF[Boolean] = F.pure(true)

      override def getGoal(nino: Nino): TestF[Option[SavingsGoal]] =
        goalsOrException match {
          case Right(events) =>
            F.pure {
              events.sortBy(_.date)(localDateTimeOrdering.reverse).headOption.flatMap {
                case SavingsGoalSetEvent(_, amount, _, name, _, _) => Some(SavingsGoal(amount))
                case _                                             => None
              }
            }
          case Left(t) => F.raiseError(t)
        }

      // This should never get called as part of the account service
      override def getGoalSetEvents(): TestF[List[SavingsGoalSetEvent]] = ???
      override def getGoalSetEvents(nino: Nino): Future[Either[ErrorInfo, List[SavingsGoalSetEvent]]] = ???

      override def setTestGoal(
        nino:   Nino,
        amount: Option[Double],
        name:   Option[String],
        date:   LocalDate
      ): TestF[Unit] = ???

      override def updateExpireAt(
        nino:     Nino,
        expireAt: LocalDateTime
      ): TestF[Unit] = F.unit

      override def updateExpireAt(): TestF[Unit] = F.unit

    }

  private def fakeMongoUpdateService: MongoUpdateService[TestF] =
    new MongoUpdateService[TestF] {

      override def updateExpireAtByNino(
        nino:     Nino,
        expireAt: LocalDateTime
      ): TestF[Unit] =
        F.pure()

    }

  object ShouldNotBeCalledGetAccount extends HelpToSaveGetAccount[TestF] {

    override def getAccount(
      nino:        Nino
    )(implicit hc: HeaderCarrier
    ): TestF[Either[ErrorInfo, Option[HelpToSaveAccount]]] =
      new RuntimeException("HelpToSaveGetAccount.getAccount should not be called in this situation")
        .raiseError[TestF, Either[ErrorInfo, Option[HelpToSaveAccount]]]
  }
}
