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

package uk.gov.hmrc.mobilehelptosave.controllers.helpToSave

import eu.timepit.refined.auto.*
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, verify}
import org.scalatest.{OneInstancePerTest, OptionValues}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.Helpers.*
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveGetTransactions
import uk.gov.hmrc.mobilehelptosave.controllers.{AlwaysAuthorisedWithIds, HelpToSaveController}
import uk.gov.hmrc.mobilehelptosave.domain.types.JourneyId
import uk.gov.hmrc.mobilehelptosave.domain.{ErrorInfo, SavingsGoal}
import uk.gov.hmrc.mobilehelptosave.repository.SavingsGoalEventRepo
import uk.gov.hmrc.mobilehelptosave.services.{AccountService, HtsSavingsUpdateService}
import uk.gov.hmrc.mobilehelptosave.support.{LoggerStub, ShutteringMocking}
import uk.gov.hmrc.mobilehelptosave.{AccountTestData, SavingsGoalTestData, TransactionTestData}

import java.time.{LocalDate, Month, YearMonth}
import java.time.temporal.ChronoUnit.*
import java.time.temporal.TemporalAdjusters
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

//noinspection TypeAnnotation
class GetSavingsUpdateSpec
    extends AnyWordSpecLike
      with Matchers
      with FutureAwaits
      with OptionValues
      with TransactionTestData
      with AccountTestData
      with DefaultAwaitTimeout
      with MockitoSugar
      with LoggerStub
      with OneInstancePerTest
      with TestSupport
      with ShutteringMocking
      with SavingsGoalTestData {

  val jid: JourneyId = JourneyId.from("02940b73-19cc-4c31-80d3-f4deb851c707").toOption.get

  "getSavingsUpdate" should {
    "ensure user is logged in and has a NINO by checking permissions using AuthorisedWithIds" in {
      if (MONTHS.between(YearMonth.of(2020, 2), YearMonth.now()) > 12)
        isForbiddenIfNotAuthorisedForUser { controller =>
          status(controller.getSavingsUpdate(jid)(FakeRequest())) shouldBe FORBIDDEN
        }
    }
  }

  "getSavingsUpdate" when {
    "logged in user's NINO matches NINO in URL" should {
      "return 200 with the users savings update" in new AuthorisedTestScenario with HelpToSaveMocking {
        accountReturns(
          Right(
            Some(
              savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(6),
                                                        savingsGoal     = Some(SavingsGoal(Some(10.0))))
            )
          )
        )
        helpToSaveGetTransactionsReturns(Future successful Right(transactionsDateDynamic))
        getGoalSetEvents(Future successful Right(dateDynamicSavingsGoalData))

        val savingsUpdate = controller.getSavingsUpdate(jid)(FakeRequest())
        status(savingsUpdate) shouldBe OK
        val jsonBody = contentAsJson(savingsUpdate)
        (jsonBody \ "reportStartDate")
          .as[LocalDate] shouldBe LocalDate.now().minusMonths(6).`with`(TemporalAdjusters.firstDayOfMonth())
        (jsonBody \ "reportEndDate")
          .as[LocalDate]                                                               shouldBe LocalDate.now().minusMonths(1).`with`(TemporalAdjusters.lastDayOfMonth())
        (jsonBody \ "accountOpenedYearMonth").as[String]                               shouldBe YearMonth.now().minusMonths(6).toString
        (jsonBody \ "savingsUpdate").isDefined                                         shouldBe true
        (jsonBody \ "savingsUpdate" \ "savedInPeriod").as[BigDecimal]                  shouldBe 137.61
        (jsonBody \ "savingsUpdate" \ "savedByMonth").isDefined                        shouldBe true
        (jsonBody \ "savingsUpdate" \ "savedByMonth" \ "monthsSaved").as[Int]          shouldBe 4
        (jsonBody \ "savingsUpdate" \ "savedByMonth" \ "numberOfMonths").as[Int]       shouldBe 6
        (jsonBody \ "savingsUpdate" \ "goalsReached").isDefined                        shouldBe true
        (jsonBody \ "savingsUpdate" \ "goalsReached" \ "currentAmount").as[Double]     shouldBe 10.0
        (jsonBody \ "savingsUpdate" \ "goalsReached" \ "numberOfTimesReached").as[Int] shouldBe 2
        (jsonBody \ "savingsUpdate" \ "amountEarnedTowardsBonus").as[BigDecimal]       shouldBe 120.00
        (jsonBody \ "bonusUpdate").isDefined                                           shouldBe true
        (jsonBody \ "bonusUpdate" \ "currentBonusTerm").as[String]                     shouldBe "First"
        (jsonBody \ "bonusUpdate" \ "monthsUntilBonus").as[Int]                        shouldBe 19
        (jsonBody \ "bonusUpdate" \ "currentBonus").as[BigDecimal]                     shouldBe 90.99
        (jsonBody \ "bonusUpdate" \ "highestBalance").as[BigDecimal]                   shouldBe 300.00
        (jsonBody \ "bonusUpdate" \ "potentialBonusAtCurrentRate").as[BigDecimal]      shouldBe 268.14
        (jsonBody \ "bonusUpdate" \ "potentialBonusWithFiveMore").as[BigDecimal]       shouldBe 313.17
      }

      "do not return savings update section if no transactions found for reporting period" in new AuthorisedTestScenario
        with HelpToSaveMocking {
        accountReturns(Right(Some(mobileHelpToSaveAccount)))
        helpToSaveGetTransactionsReturns(Future successful Right(transactionsSortedInMobileHelpToSaveOrder))
        getGoalSetEvents(Future successful Right(dateDynamicSavingsGoalData))
        val expectedStartDate =
          if (YearMonth.now().getMonth == Month.JANUARY)
            LocalDate.now().minusMonths(1).`with`(TemporalAdjusters.firstDayOfYear())
          else LocalDate.now().`with`(TemporalAdjusters.firstDayOfYear())

        val savingsUpdate = controller.getSavingsUpdate(jid)(FakeRequest())
        status(savingsUpdate) shouldBe OK
        val jsonBody = contentAsJson(savingsUpdate)
        (jsonBody \ "reportStartDate")
          .as[LocalDate] shouldBe expectedStartDate
        (jsonBody \ "reportEndDate")
          .as[LocalDate]                                 shouldBe LocalDate.now().minusMonths(1).`with`(TemporalAdjusters.lastDayOfMonth())
        (jsonBody \ "accountOpenedYearMonth").as[String] shouldBe mobileHelpToSaveAccount.openedYearMonth.toString
        (jsonBody \ "savingsUpdate").isEmpty             shouldBe true
      }
    }

    "the user has no Help to Save account according to AccountService" should {
      "return 404" in new AuthorisedTestScenario with HelpToSaveMocking {

        accountReturns(Right(None))

        val resultF = controller.getSavingsUpdate(jid)(FakeRequest())
        status(resultF) shouldBe 404
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "code").as[String] shouldBe "ACCOUNT_NOT_FOUND"
        (jsonBody \ "message")
          .as[String] shouldBe "No Help to Save account exists for the specified NINO"
        verify(slf4jLoggerStub, never()).warn(any[String])
      }
    }

    "AccountService returns an error" should {
      "return 500" in new AuthorisedTestScenario with HelpToSaveMocking {

        accountReturns(Left(ErrorInfo.General))

        val resultF = controller.getSavingsUpdate(jid)(FakeRequest())
        status(resultF) shouldBe 500
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "code").as[String] shouldBe ErrorInfo.General.code
      }
    }

    "helpToSaveShuttered = true" should {
      """return 521 "shuttered": true""" in {
        val accountService            = mock[AccountService]
        val helpToSaveGetTransactions = mock[HelpToSaveGetTransactions]
        val savingsGoalEventRepo      = mock[SavingsGoalEventRepo]
        val controller = new HelpToSaveController(
          logger,
          accountService,
          helpToSaveGetTransactions,
          new AlwaysAuthorisedWithIds(nino, trueShuttering),
          new HtsSavingsUpdateService,
          savingsGoalEventRepo,
          stubControllerComponents()
        )

        val resultF = controller.getSavingsUpdate(jid)(FakeRequest())
        status(resultF) shouldBe 521
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "shuttered").as[Boolean] shouldBe true
        (jsonBody \ "title").as[String]      shouldBe "Shuttered"
        (jsonBody \ "message")
          .as[String] shouldBe "HTS is currently not available"
      }
    }
  }
}
