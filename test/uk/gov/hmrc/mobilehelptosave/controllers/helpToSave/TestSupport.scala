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

package uk.gov.hmrc.mobilehelptosave.controllers

package helpToSave

import java.util.UUID.randomUUID
import cats.syntax.either._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Assertion, OneInstancePerTest}
import play.api.test.Helpers.stubControllerComponents
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveGetTransactions
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.repository.{SavingsGoalEventRepo, SavingsGoalSetEvent}
import uk.gov.hmrc.mobilehelptosave.services.{AccountService, HtsSavingsUpdateService}
import uk.gov.hmrc.mobilehelptosave.support.LoggerStub

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

//noinspection TypeAnnotation
trait TestSupport {
  self: MockFactory with LoggerStub with OneInstancePerTest =>
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val generator = new Generator(0)
  val nino      = generator.nextNino
  val otherNino = generator.nextNino
  val journeyId = randomUUID().toString

  def isForbiddenIfNotAuthorisedForUser(authorisedActionForNino: HelpToSaveController => Assertion): Assertion = {
    val accountService            = mock[AccountService[Future]]
    val helpToSaveGetTransactions = mock[HelpToSaveGetTransactions[Future]]
    val savingsGoalEventRepo      = mock[SavingsGoalEventRepo[Future]]
    val controller =
      new HelpToSaveController(logger,
                               accountService,
                               helpToSaveGetTransactions,
                               NeverAuthorisedWithIds,
                               new HtsSavingsUpdateService,
                               savingsGoalEventRepo,
                               stubControllerComponents())
    authorisedActionForNino(controller)
  }

  trait AuthorisedTestScenario {
    val accountService            = mock[AccountService[Future]]
    val helpToSaveGetTransactions = mock[HelpToSaveGetTransactions[Future]]
    val savingsGoalEventRepo      = mock[SavingsGoalEventRepo[Future]]

    val controller: HelpToSaveController =
      new HelpToSaveController(
        logger,
        accountService,
        helpToSaveGetTransactions,
        new AlwaysAuthorisedWithIds(nino),
        new HtsSavingsUpdateService,
        savingsGoalEventRepo,
        stubControllerComponents()
      )
  }

  trait HelpToSaveMocking {
    scenario: AuthorisedTestScenario =>

    def accountReturns(stubbedResponse: Either[ErrorInfo, Option[Account]]) =
      (accountService
        .account(_: Nino)(_: HeaderCarrier))
        .expects(nino, *)
        .returning(Future.successful(stubbedResponse))

    def helpToSaveGetTransactionsReturns(stubbedResponse: Future[Either[ErrorInfo, Transactions]]) =
      (helpToSaveGetTransactions
        .getTransactions(_: Nino)(_: HeaderCarrier))
        .expects(nino, *)
        .returning(stubbedResponse)

    def getGoalSetEvents(stubbedResponse: Future[Either[ErrorInfo, Seq[SavingsGoalSetEvent]]]) =
      (savingsGoalEventRepo
        .getGoalSetEvents(_: Nino))
        .expects(nino)
        .returning(stubbedResponse)

    def setSavingsGoalReturns(
      expectedNino:    Nino,
      expectedAmount:  Option[Double],
      stubbedResponse: Either[ErrorInfo, Unit]
    ) =
      (accountService
        .setSavingsGoal(_: Nino, _: SavingsGoal)(_: HeaderCarrier))
        .expects(where { (nino, amount, _) =>
          nino == expectedNino && amount.goalAmount == expectedAmount
        })
        .returning(Future.successful(stubbedResponse))

    def deleteSavingsGoalExpects(expectedNino: Nino) =
      (accountService
        .deleteSavingsGoal(_: Nino)(_: HeaderCarrier))
        .expects(where { (suppliedNino: Nino, _) =>
          suppliedNino == expectedNino
        })
        .returning(Future.successful(().asRight))
  }
}
