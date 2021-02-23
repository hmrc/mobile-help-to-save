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

package uk.gov.hmrc.mobilehelptosave.controllers.helpToSave

import eu.timepit.refined.auto._
import org.joda.time.Months
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OneInstancePerTest, OptionValues, WordSpec}
import play.api.libs.json.Json
import play.api.test.Helpers.{contentAsJson, status, _}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveGetTransactions
import uk.gov.hmrc.mobilehelptosave.controllers.{AlwaysAuthorisedWithIds, HelpToSaveController}
import uk.gov.hmrc.mobilehelptosave.domain.{CurrentBonusTerm, ErrorInfo, SavingsGoal}
import uk.gov.hmrc.mobilehelptosave.repository.{SavingsGoalEventRepo, SavingsGoalSetEvent}
import uk.gov.hmrc.mobilehelptosave.scalatest.SchemaMatchers
import uk.gov.hmrc.mobilehelptosave.services.{AccountService, HtsSavingsUpdateService}
import uk.gov.hmrc.mobilehelptosave.support.{LoggerStub, ShutteringMocking}
import uk.gov.hmrc.mobilehelptosave.{AccountTestData, SavingsGoalTestData, TransactionTestData}

import java.time.{LocalDate, YearMonth}
import java.time.temporal.ChronoUnit._
import java.time.temporal.{TemporalAdjuster, TemporalAdjusters}
import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

//noinspection TypeAnnotation
class GetSavingsUpdateSpec
    extends WordSpec
    with Matchers
    with SchemaMatchers
    with FutureAwaits
    with OptionValues
    with TransactionTestData
    with AccountTestData
    with DefaultAwaitTimeout
    with MockFactory
    with LoggerStub
    with OneInstancePerTest
    with TestSupport
    with ShutteringMocking
    with SavingsGoalTestData {

  "getSavingsUpdate" should {
    "ensure user is logged in and has a NINO by checking permissions using AuthorisedWithIds" in {
      if (MONTHS.between(YearMonth.of(2020, 2), YearMonth.now()) > 12)
        isForbiddenIfNotAuthorisedForUser { controller =>
          status(controller.getSavingsUpdate("02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())) shouldBe FORBIDDEN
        }
    }
  }

  "getSavingsUpdate" when {
    "logged in user's NINO matches NINO in URL" should {
      "return 200 with the users savings update" in new AuthorisedTestScenario with HelpToSaveMocking {
        accountReturns(
          Right(
            Some(
              mobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(6),
                                           savingsGoal     = Some(SavingsGoal(Some(10.0))))
            )
          )
        )
        helpToSaveGetTransactionsReturns(Future successful Right(transactionsDateDynamic))
        getGoalSetEvents(Future successful Right(dateDynamicSavingsGoalData))

        val savingsUpdate = controller.getSavingsUpdate("02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())
        status(savingsUpdate) shouldBe OK
        val jsonBody = contentAsJson(savingsUpdate)
        (jsonBody \ "reportStartDate")
          .as[LocalDate] shouldBe LocalDate.now().minusMonths(6).`with`(TemporalAdjusters.firstDayOfMonth())
        (jsonBody \ "reportEndDate")
          .as[LocalDate]                                                               shouldBe LocalDate.now().minusMonths(1).`with`(TemporalAdjusters.lastDayOfMonth())
        (jsonBody \ "accountOpenedYearMonth").as[String]                               shouldBe YearMonth.now().minusMonths(6).toString
        (jsonBody \ "savingsUpdate").isDefined                                         shouldBe true
        (jsonBody \ "savingsUpdate" \ "savedInPeriod").as[BigDecimal]                  shouldBe BigDecimal(137.61)
        (jsonBody \ "savingsUpdate" \ "savedByMonth").isDefined                        shouldBe true
        (jsonBody \ "savingsUpdate" \ "savedByMonth" \ "monthsSaved").as[Int]          shouldBe 4
        (jsonBody \ "savingsUpdate" \ "savedByMonth" \ "numberOfMonths").as[Int]       shouldBe 6
        (jsonBody \ "savingsUpdate" \ "goalsReached").isDefined                        shouldBe true
        (jsonBody \ "savingsUpdate" \ "goalsReached" \ "currentGoalAmount").as[Double] shouldBe 10.0
        (jsonBody \ "savingsUpdate" \ "goalsReached" \ "numberOfTimesReached").as[Int] shouldBe 2
        (jsonBody \ "bonusUpdate").isDefined                                           shouldBe true
        (jsonBody \ "bonusUpdate" \ "currentBonus").as[BigDecimal]                     shouldBe BigDecimal(90.99)
        (jsonBody \ "bonusUpdate" \ "highestBalance").as[BigDecimal]                   shouldBe BigDecimal(181.98)
      }

      "do not return savings update section if no transactions found for reporting period" in new AuthorisedTestScenario
        with HelpToSaveMocking {
        accountReturns(Right(Some(mobileHelpToSaveAccount)))
        helpToSaveGetTransactionsReturns(Future successful Right(transactionsSortedInMobileHelpToSaveOrder))
        getGoalSetEvents(Future successful Right(dateDynamicSavingsGoalData))

        val savingsUpdate = controller.getSavingsUpdate("02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())
        status(savingsUpdate) shouldBe OK
        val jsonBody = contentAsJson(savingsUpdate)
        (jsonBody \ "reportStartDate")
          .as[LocalDate] shouldBe LocalDate.now().`with`(TemporalAdjusters.firstDayOfYear())
        (jsonBody \ "reportEndDate")
          .as[LocalDate]                                 shouldBe LocalDate.now().minusMonths(1).`with`(TemporalAdjusters.lastDayOfMonth())
        (jsonBody \ "accountOpenedYearMonth").as[String] shouldBe mobileHelpToSaveAccount.openedYearMonth.toString
        (jsonBody \ "savingsUpdate").isEmpty             shouldBe true
      }
    }

    "the user has no Help to Save account according to AccountService" should {
      "return 404" in new AuthorisedTestScenario with HelpToSaveMocking {

        accountReturns(Right(None))

        val resultF = controller.getSavingsUpdate("02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())
        status(resultF) shouldBe 404
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "code").as[String] shouldBe "ACCOUNT_NOT_FOUND"
        (jsonBody \ "message")
          .as[String] shouldBe "No Help to Save account exists for the specified NINO"

        (slf4jLoggerStub.warn(_: String)) verify * never ()
      }
    }

    "AccountService returns an error" should {
      "return 500" in new AuthorisedTestScenario with HelpToSaveMocking {

        accountReturns(Left(ErrorInfo.General))

        val resultF = controller.getSavingsUpdate("02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())
        status(resultF) shouldBe 500
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "code").as[String] shouldBe ErrorInfo.General.code
      }
    }

    "helpToSaveShuttered = true" should {
      """return 521 "shuttered": true""" in {
        val accountService            = mock[AccountService[Future]]
        val helpToSaveGetTransactions = mock[HelpToSaveGetTransactions[Future]]
        val savingsGoalEventRepo      = mock[SavingsGoalEventRepo[Future]]
        val controller = new HelpToSaveController(
          logger,
          accountService,
          helpToSaveGetTransactions,
          new AlwaysAuthorisedWithIds(nino, trueShuttering),
          new HtsSavingsUpdateService,
          savingsGoalEventRepo,
          stubControllerComponents()
        )

        val resultF = controller.getSavingsUpdate("02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())
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
