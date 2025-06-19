/*
 * Copyright 2024 HM Revenue & Customs
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

import cats.syntax.applicativeError.*
import org.mockito.Mockito.{never, verify}
import play.api.LoggerLike
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.{AccountTestData, TransactionTestData}
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveAccount, HelpToSaveBonusTerm, HelpToSaveEnrolmentStatus, HelpToSaveGetAccount, HelpToSaveGetTransactions, HttpClientV2Helper}
import uk.gov.hmrc.mobilehelptosave.controllers.TestSandboxDataConfig.inAppPaymentsEnabled
import uk.gov.hmrc.mobilehelptosave.domain.*
import uk.gov.hmrc.mobilehelptosave.repository.{SavingsGoalEvent, SavingsGoalEventRepo, SavingsGoalSetEvent}

import java.time.{LocalDate, LocalDateTime, YearMonth}
import scala.concurrent.{ExecutionContext, Future}

class AccountServiceSpec extends HttpClientV2Helper with AccountTestData with TransactionTestData {

  private val testConfig = TestAccountServiceConfig(inAppPaymentsEnabled = false, savingsGoalsEnabled = false)
  val logger = mock[LoggerLike]
  implicit val ex : ExecutionContext = ExecutionContext.global
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
        new HtsAccountService(logger,
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
      val result = service.account(nino).futureValue.map(_.map(_.copy(daysRemainingInMonth = 1)))
      result mustBe Right(Some(mobileHelpToSaveAccount.copy(savingsGoalsEnabled = testConfig.savingsGoalsEnabled)))
    }

    "fold the value of the 'savingsGoalEnabled' config into the returned account" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val fakeGetTransactions  = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))

      val config = testConfig.copy(savingsGoalsEnabled = true)
      val service =
        new HtsAccountService(logger,
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
      val result = service.account(nino).futureValue.map(_.map(_.copy(daysRemainingInMonth = 1)))
      result mustBe Right(Some(mobileHelpToSaveAccount.copy(savingsGoalsEnabled = true)))

    }

    "allow inAppPaymentsEnabled to be overridden with configuration" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val fakeGetTransactions  = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))
      val service =
        new HtsAccountService(logger,
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
      val result = service.account(nino).futureValue.map(_.map(_.copy(daysRemainingInMonth = 1)))
      result mustBe Right(
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
        new HtsAccountService(logger,
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
      val result = service.account(nino).futureValue.map(_.map(_.copy(daysRemainingInMonth = 1)))
      result mustBe Right(
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
        new HtsAccountService(logger,
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
      val result = service.account(nino).futureValue.map(_.map(_.copy(daysRemainingInMonth = 1)))
      result.map(_.map(_.potentialBonus)) mustBe Right(Some(Some(BigDecimal("0"))))
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
        new HtsAccountService(logger,
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
      val result = service.account(nino).futureValue.map(_.map(_.copy(daysRemainingInMonth = 1)))
      result.map(_.map(_.potentialBonus)) mustBe Right(Some(Some(BigDecimal("12"))))
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
        new HtsAccountService(logger,
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
      val result = service.account(nino).futureValue.map(_.map(_.copy(daysRemainingInMonth = 1)))
      result.map(_.map(_.potentialBonus)) mustBe Right(Some(Some(BigDecimal("600"))))
    }

    // this is to avoid unnecessary load on NS&I, see NGC-3799
    "return None without attempting to get account from help-to-save when the user is not enrolled" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(false))
      val fakeGetTransactions  = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))
      val service =
        new HtsAccountService(logger,
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
      service.account(nino).futureValue mustBe Right(None)

      (logger.warn(_: String))
    }

    "return None and log a warning when user is enrolled according to help-to-save but no account exists in NS&I" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(None))
      val fakeGetTransactions  = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))
      val service =
        new HtsAccountService(logger,
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

      service.account(nino).futureValue mustBe Right(None)

      (logger
        .warn(_: String)) (s"${nino.value} was enrolled according to help-to-save microservice but no account was found in NS&I - data is inconsistent")

    }

    "not call either fetchSavingsGoal or fetchNSAndIAccount if the user isn't enrolled" in {
      val fakeEnrolmentStatus = fakeHelpToSaveEnrolmentStatus(nino, Right(false))

      val fakeGetAccount = new HelpToSaveGetAccount {
        override def getAccount(
          nino:        Nino
        )(implicit hc: HeaderCarrier
        ): Future[Either[ErrorInfo, Option[HelpToSaveAccount]]] =
          fail("getAccount should not have been called")
      }
      val fakeGetTransactions = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))

      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))

      val service =
        new HtsAccountService(logger,
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

      service.account(nino).futureValue mustBe Right(None)
    }

    "return errors returned by connector.enrolmentStatus" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Left(ErrorInfo.General))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val fakeGetTransactions  = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))
      val service =
        new HtsAccountService(logger,
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
      service.account(nino).futureValue mustBe Left(ErrorInfo.General)
    }

    "return errors returned by connector.getAccount" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Left(ErrorInfo.General))
      val fakeGetTransactions  = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))
      val service =
        new HtsAccountService(logger,
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
      service.account(nino).futureValue mustBe Left(ErrorInfo.General)
    }

    "return errors if savingsGoalRepo.get throws exception" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val fakeGetTransactions  = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Left(new Exception("test exception")))
      val service =
        new HtsAccountService(logger,
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
      service.account(nino).futureValue mustBe Left(ErrorInfo.General)
    }
  }

  private def fakeBalanceMilestoneService: BalanceMilestonesService =
    new BalanceMilestonesService {

      override def balanceMilestoneCheck(
        nino:                        Nino,
        currentBalance:              BigDecimal,
        secondPeriodBonusPaidByDate: LocalDate
      )(implicit hc:                 HeaderCarrier,
        ec:                          ExecutionContext
      ): Future[MilestoneCheckResult] =
        Future.successful(CouldNotCheck)

    }

  private def fakeBonusPeriodMilestoneService: BonusPeriodMilestonesService =
    new BonusPeriodMilestonesService {

      override def bonusPeriodMilestoneCheck(
        nino:             Nino,
        bonusTerms:       Seq[BonusTerm],
        currentBalance:   BigDecimal,
        currentBonusTerm: CurrentBonusTerm.Value,
        accountClosed:    Boolean
      )(implicit hc:      HeaderCarrier,
        ex: ExecutionContext
      ): Future[MilestoneCheckResult] = Future.successful(CouldNotCheck)
    }

  private def fakeBonusReachedMilestoneService: BonusReachedMilestonesService =
    new BonusReachedMilestonesService {

      override def bonusReachedMilestoneCheck(
        nino:             Nino,
        bonusTerms:       Seq[BonusTerm],
        currentBonusTerm: CurrentBonusTerm.Value
      )(implicit hc:      HeaderCarrier,
        ex: ExecutionContext
      ): Future[MilestoneCheckResult] = Future.successful(CouldNotCheck)
    }

  private def fakeHelpToSaveEnrolmentStatus(
    expectedNino:    Nino,
    enrolledOrError: Either[ErrorInfo, Boolean]
  ): HelpToSaveEnrolmentStatus =
    new HelpToSaveEnrolmentStatus {

      override def enrolmentStatus()(implicit hc: HeaderCarrier): Future[Either[ErrorInfo, Boolean]] = {
        nino mustBe expectedNino
        hc   mustBe passedHc

        Future.successful(enrolledOrError)
      }
    }

  private def fakeHelpToSaveGetAccount(
    expectedNino:   Nino,
    accountOrError: Either[ErrorInfo, Option[HelpToSaveAccount]]
  ): HelpToSaveGetAccount =
    new HelpToSaveGetAccount {

      override def getAccount(
        nino:        Nino
      )(implicit hc: HeaderCarrier
      ): Future[Either[ErrorInfo, Option[HelpToSaveAccount]]] = {
        nino mustBe expectedNino
        hc   mustBe passedHc

        Future.successful(accountOrError)
      }
    }

  private def fakeHelpToSaveGetTransactions(
    transactionsOrError: Either[ErrorInfo, Transactions]
  ): HelpToSaveGetTransactions =
    new HelpToSaveGetTransactions {

      override def getTransactions(nino: Nino)(implicit hc: HeaderCarrier): Future[Either[ErrorInfo, Transactions]] =
        Future.successful(transactionsOrError)
    }

  private def fakeSavingsGoalEventsRepo(
    expectedNino:     Nino,
    goalsOrException: Either[Throwable, List[SavingsGoalEvent]]
  ): SavingsGoalEventRepo =
    new SavingsGoalEventRepo {

      override def setGoal(
        nino:                        Nino,
        amount:                      Option[Double] = None,
        name:                        Option[String] = None,
        secondPeriodBonusPaidByDate: LocalDate
      ): Future[Unit] = {
        nino mustBe expectedNino
        Future.unit
      }

      override def getEvents(nino: Nino): Future[List[SavingsGoalEvent]] = {
        nino mustBe expectedNino
        goalsOrException match {
          case Left(t)     => Future.failed(t)
          case Right(goal) => Future.successful(goal)
        }
      }

      override def deleteGoal(
        nino:                        Nino,
        secondPeriodBonusPaidByDate: LocalDate
      ): Future[Unit] = {
        nino mustBe expectedNino
        Future.unit
      }

      override def clearGoalEvents(): Future[Boolean] = Future.successful(true)

      override def getGoal(nino: Nino): Future[Option[SavingsGoal]] =
        goalsOrException match {
          case Right(events) =>
            Future.successful {
              events.sortBy(_.date)(InstantOrdering.reverse).headOption.flatMap {
                case SavingsGoalSetEvent(_, amount, _, name, _, _) => Some(SavingsGoal(amount))
                case _                                             => None
              }
            }
          case Left(t) => Future.failed(t)
        }

      // This should never get called as part of the account service
      override def getGoalSetEvents: Future[List[SavingsGoalSetEvent]] = ???
      override def getGoalSetEvents(nino: Nino): Future[Either[ErrorInfo, List[SavingsGoalSetEvent]]] = ???

      override def setTestGoal(
        nino:   Nino,
        amount: Option[Double],
        name:   Option[String],
        date:   LocalDate
      ): Future[Unit] = ???

      override def updateExpireAt(
        nino:     Nino,
        expireAt: LocalDateTime
      ): Future[Unit] = Future.unit

      override def updateExpireAt(): Future[Unit] = Future.unit

    }

  private def fakeMongoUpdateService: MongoUpdateService =
    new MongoUpdateService {

      override def updateExpireAtByNino(
        nino:     Nino,
        expireAt: LocalDateTime
      ): Future[Unit] =
        Future.successful(())

    }

  object ShouldNotBeCalledGetAccount extends HelpToSaveGetAccount {

    override def getAccount(
      nino:        Nino
    )(implicit hc: HeaderCarrier
    ): Future[Either[ErrorInfo, Option[HelpToSaveAccount]]] =
      new RuntimeException("HelpToSaveGetAccount.getAccount should not be called in this situation")
        .raiseError[Future, Either[ErrorInfo, Option[HelpToSaveAccount]]]
  }
}
