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

///*
// * Copyright 2023 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.mobilehelptosave.services
//
//import cats.syntax.either._
//import org.scalatest.EitherValues
//import uk.gov.hmrc.domain.Nino
//import uk.gov.hmrc.http.HeaderCarrier
//import uk.gov.hmrc.mobilehelptosave.AccountTestData
//import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveAccount, HelpToSaveEnrolmentStatus, HelpToSaveGetAccount, HelpToSaveGetTransactions}
//import uk.gov.hmrc.mobilehelptosave.domain._
//import uk.gov.hmrc.mobilehelptosave.repository.{SavingsGoalEvent, SavingsGoalEventRepo, SavingsGoalSetEvent}
//import uk.gov.hmrc.mobilehelptosave.support.{BaseSpec, TestF}
//
//import java.time.{LocalDate, LocalDateTime}
//import scala.concurrent.Future
//
//class SetSavingsGoalSpec extends BaseSpec with TestF with AccountTestData with EitherValues {
//
//  private val testConfig = TestAccountServiceConfig(inAppPaymentsEnabled = false, savingsGoalsEnabled = false)
//
//  private val testMilestonesConfig =
//    TestMilestonesConfig(balanceMilestoneCheckEnabled      = true,
//                         bonusPeriodMilestoneCheckEnabled  = true,
//                         bonusReachedMilestoneCheckEnabled = true)
//
//  private implicit val passedHc: HeaderCarrier = HeaderCarrier()
//
//  "setSavingsGoal" should {
//    "return Right[Unit] if successful" in {
//      val fakeEnrolmentStatus = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
//      val fakeGetAccount      = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
//      val fakeGetTransactions = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))
//      val fakeGoalsRepo       = fakeSavingsGoalEventsRepo(nino)
//
//      val service =
//        new HtsAccountService[TestF](logger,
//                                     testConfig.copy(inAppPaymentsEnabled = true),
//                                     fakeEnrolmentStatus,
//                                     fakeGetAccount,
//                                     fakeGoalsRepo,
//                                     fakeBalanceMilestoneService,
//                                     fakeBonusPeriodMilestoneService,
//                                     fakeBonusReachedMilestoneService,
//                                     fakeMongoUpdateService,
//                                     new HtsSavingsUpdateService,
//                                     fakeGetTransactions,
//                                     testMilestonesConfig)
//
//      service.setSavingsGoal(nino, SavingsGoal(Some(1.0))).unsafeGet shouldBe Right(())
//    }
//
//    "return a ValidationError if the goal is < 1.0" in {
//      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
//      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
//      val fakeGetTransactions  = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))
//      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino)
//
//      val service =
//        new HtsAccountService[TestF](logger,
//                                     testConfig.copy(inAppPaymentsEnabled = true),
//                                     fakeEnrolmentStatus,
//                                     fakeGetAccount,
//                                     savingsGoalEventRepo,
//                                     fakeBalanceMilestoneService,
//                                     fakeBonusPeriodMilestoneService,
//                                     fakeBonusReachedMilestoneService,
//                                     fakeMongoUpdateService,
//                                     new HtsSavingsUpdateService,
//                                     fakeGetTransactions,
//                                     testMilestonesConfig)
//
//      service.setSavingsGoal(nino, SavingsGoal(Some(0.99))).unsafeGet.left.value shouldBe a[ErrorInfo.ValidationError]
//    }
//
//    "return a ValidationError if the goal is > maximum for month" in {
//      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
//      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
//      val fakeGetTransactions  = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))
//      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino)
//
//      val service =
//        new HtsAccountService[TestF](logger,
//                                     testConfig.copy(inAppPaymentsEnabled = true),
//                                     fakeEnrolmentStatus,
//                                     fakeGetAccount,
//                                     savingsGoalEventRepo,
//                                     fakeBalanceMilestoneService,
//                                     fakeBonusPeriodMilestoneService,
//                                     fakeBonusReachedMilestoneService,
//                                     fakeMongoUpdateService,
//                                     new HtsSavingsUpdateService,
//                                     fakeGetTransactions,
//                                     testMilestonesConfig)
//
//      service
//        .setSavingsGoal(nino, SavingsGoal(Some(helpToSaveAccount.maximumPaidInThisMonth.toDouble + 0.01)))
//        .unsafeGet
//        .left
//        .value shouldBe a[ErrorInfo.ValidationError]
//    }
//
//    "return an AccountNotFound if the nino does not have an account with NS&I" in {
//      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
//      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(None))
//      val fakeGetTransactions  = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))
//      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino)
//
//      val service =
//        new HtsAccountService[TestF](logger,
//                                     testConfig.copy(inAppPaymentsEnabled = true),
//                                     fakeEnrolmentStatus,
//                                     fakeGetAccount,
//                                     savingsGoalEventRepo,
//                                     fakeBalanceMilestoneService,
//                                     fakeBonusPeriodMilestoneService,
//                                     fakeBonusReachedMilestoneService,
//                                     fakeMongoUpdateService,
//                                     new HtsSavingsUpdateService,
//                                     fakeGetTransactions,
//                                     testMilestonesConfig)
//
//      service.setSavingsGoal(nino, SavingsGoal(Some(1.0))).unsafeGet.left.value shouldBe ErrorInfo.AccountNotFound
//    }
//
//    "return a General error if the repo throws a Non-Fatal exception" in {
//      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
//      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
//      val fakeGetTransactions  = fakeHelpToSaveGetTransactions(Right(Transactions(Seq.empty)))
//      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, setGoalResponse = Left(new Exception("non fatal")))
//
//      val service =
//        new HtsAccountService[TestF](logger,
//                                     testConfig.copy(inAppPaymentsEnabled = true),
//                                     fakeEnrolmentStatus,
//                                     fakeGetAccount,
//                                     savingsGoalEventRepo,
//                                     fakeBalanceMilestoneService,
//                                     fakeBonusPeriodMilestoneService,
//                                     fakeBonusReachedMilestoneService,
//                                     fakeMongoUpdateService,
//                                     new HtsSavingsUpdateService,
//                                     fakeGetTransactions,
//                                     testMilestonesConfig)
//
//      service.setSavingsGoal(nino, SavingsGoal(Some(1.0))).unsafeGet.left.value shouldBe ErrorInfo.General
//    }
//  }
//
//  private def fakeBalanceMilestoneService: BalanceMilestonesService[TestF] =
//    new BalanceMilestonesService[TestF] {
//
//      override def balanceMilestoneCheck(
//        nino:                        Nino,
//        currentBalance:              BigDecimal,
//        secondPeriodBonusPaidByDate: LocalDate
//      )(implicit hc:                 HeaderCarrier
//      ): TestF[MilestoneCheckResult] =
//        F.pure(CouldNotCheck)
//    }
//
//  private def fakeBonusPeriodMilestoneService: BonusPeriodMilestonesService[TestF] =
//    new BonusPeriodMilestonesService[TestF] {
//
//      override def bonusPeriodMilestoneCheck(
//        nino:             Nino,
//        bonusTerms:       Seq[BonusTerm],
//        currentBalance:   BigDecimal,
//        currentBonusTerm: CurrentBonusTerm.Value,
//        accountClosed:    Boolean
//      )(implicit hc:      HeaderCarrier
//      ): TestF[MilestoneCheckResult] = F.pure(CouldNotCheck)
//    }
//
//  private def fakeBonusReachedMilestoneService: BonusReachedMilestonesService[TestF] =
//    new BonusReachedMilestonesService[TestF] {
//
//      override def bonusReachedMilestoneCheck(
//        nino:             Nino,
//        bonusTerms:       Seq[BonusTerm],
//        currentBonusTerm: CurrentBonusTerm.Value
//      )(implicit hc:      HeaderCarrier
//      ): TestF[MilestoneCheckResult] = F.pure(CouldNotCheck)
//    }
//
//  private def fakeHelpToSaveEnrolmentStatus(
//    expectedNino:    Nino,
//    enrolledOrError: Either[ErrorInfo, Boolean]
//  ): HelpToSaveEnrolmentStatus[TestF] =
//    new HelpToSaveEnrolmentStatus[TestF] {
//
//      override def enrolmentStatus()(implicit hc: HeaderCarrier): TestF[Either[ErrorInfo, Boolean]] = {
//        nino shouldBe expectedNino
//        hc   shouldBe passedHc
//
//        F.pure(enrolledOrError)
//      }
//    }
//
//  private def fakeHelpToSaveGetAccount(
//    expectedNino:   Nino,
//    accountOrError: Either[ErrorInfo, Option[HelpToSaveAccount]]
//  ): HelpToSaveGetAccount[TestF] =
//    new HelpToSaveGetAccount[TestF] {
//
//      override def getAccount(
//        nino:        Nino
//      )(implicit hc: HeaderCarrier
//      ): TestF[Either[ErrorInfo, Option[HelpToSaveAccount]]] = {
//        nino shouldBe expectedNino
//        hc   shouldBe passedHc
//
//        F.pure(accountOrError)
//      }
//    }
//
//  private def fakeHelpToSaveGetTransactions(
//    transactionsOrError: Either[ErrorInfo, Transactions]
//  ): HelpToSaveGetTransactions[TestF] =
//    new HelpToSaveGetTransactions[TestF] {
//
//      override def getTransactions(nino: Nino)(implicit hc: HeaderCarrier): TestF[Either[ErrorInfo, Transactions]] =
//        F.pure(transactionsOrError)
//    }
//
//  private val fUnit = F.unit
//
//  private def fakeSavingsGoalEventsRepo(
//    expectedNino:       Nino,
//    goalsOrException:   Either[Throwable, List[SavingsGoalEvent]] = List().asRight,
//    setGoalResponse:    Either[Throwable, Unit] = ().asRight,
//    deleteGoalResponse: Either[Throwable, Unit] = ().asRight
//  ): SavingsGoalEventRepo[TestF] = new SavingsGoalEventRepo[TestF] {
//
//    override def setGoal(
//      nino:                        Nino,
//      amount:                      Option[Double] = None,
//      name:                        Option[String] = None,
//      secondPeriodBonusPaidByDate: LocalDate
//    ): TestF[Unit] = {
//      nino shouldBe expectedNino
//      setGoalResponse match {
//        case Left(t)  => F.raiseError(t)
//        case Right(_) => fUnit
//      }
//    }
//
//    override def getEvents(nino: Nino): TestF[List[SavingsGoalEvent]] = {
//      nino shouldBe expectedNino
//      goalsOrException match {
//        case Left(t)     => F.raiseError(t)
//        case Right(goal) => F.pure(goal)
//      }
//    }
//
//    override def deleteGoal(
//      nino:                        Nino,
//      secondPeriodBonusPaidByDate: LocalDate
//    ): TestF[Unit] = {
//      nino shouldBe expectedNino
//      deleteGoalResponse match {
//        case Left(t)  => F.raiseError(t)
//        case Right(_) => fUnit
//      }
//    }
//
//    override def clearGoalEvents(): TestF[Boolean] = F.pure(true)
//
//    // These should never get called as part of setting a savings goal
//    override def getGoal(nino: Nino): TestF[Option[SavingsGoal]] = ???
//    override def getGoalSetEvents: TestF[List[SavingsGoalSetEvent]] = ???
//    override def getGoalSetEvents(nino: Nino): Future[Either[ErrorInfo, List[SavingsGoalSetEvent]]] = ???
//
//    override def setTestGoal(
//      nino:   Nino,
//      amount: Option[Double],
//      name:   Option[String],
//      date:   LocalDate
//    ): TestF[Unit] = ???
//
//    override def updateExpireAt(
//      nino:     Nino,
//      expireAt: LocalDateTime
//    ): TestF[Unit] = F.unit
//
//    override def updateExpireAt(): TestF[Unit] = F.unit
//
//  }
//
//  private def fakeMongoUpdateService: MongoUpdateService[TestF] =
//    new MongoUpdateService[TestF] {
//
//      override def updateExpireAtByNino(
//        nino:     Nino,
//        expireAt: LocalDateTime
//      ): TestF[Unit] =
//        F.pure()
//
//    }
//
//  object ShouldNotBeCalledGetAccount extends HelpToSaveGetAccount[TestF] {
//
//    override def getAccount(
//      nino:        Nino
//    )(implicit hc: HeaderCarrier
//    ): TestF[Either[ErrorInfo, Option[HelpToSaveAccount]]] =
//      F.raiseError(new RuntimeException("HelpToSaveGetAccount.getAccount should not be called in this situation"))
//  }
//}
