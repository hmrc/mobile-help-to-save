/*
 * Copyright 2018 HM Revenue & Customs
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

import org.scalamock.scalatest.MockFactory
import org.scalatest.{Assertion, OneInstancePerTest}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.HelpToSaveControllerConfig
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveGetTransactions
import uk.gov.hmrc.mobilehelptosave.domain.{Account, ErrorInfo, Shuttering, Transactions}
import uk.gov.hmrc.mobilehelptosave.repository.{SavingsGoalMongoModel, SavingsGoalRepo}
import uk.gov.hmrc.mobilehelptosave.services.AccountService
import uk.gov.hmrc.mobilehelptosave.support.LoggerStub

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class TestHelpToSaveControllerConfig(shuttering: Shuttering, savingsGoalsEnabled: Boolean)
  extends HelpToSaveControllerConfig

//noinspection TypeAnnotation
trait TestSupport {
  self: MockFactory with LoggerStub with OneInstancePerTest =>
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val generator = new Generator(0)
  val nino      = generator.nextNino
  val otherNino = generator.nextNino

  val trueShuttering  = Shuttering(shuttered = true, "Shuttered", "HTS is currently not available")
  val falseShuttering = Shuttering(shuttered = false, "", "")

  val config = TestHelpToSaveControllerConfig(falseShuttering, savingsGoalsEnabled = false)

  def isForbiddenIfNotAuthorisedForUser(authorisedActionForNino: HelpToSaveController => Assertion): Assertion = {
    val accountService = mock[AccountService]
    val helpToSaveGetTransactions = mock[HelpToSaveGetTransactions]
    val savingsGoalRepo = mock[SavingsGoalRepo]
    val controller = new HelpToSaveController(logger, accountService, helpToSaveGetTransactions, NeverAuthorisedWithIds, config, savingsGoalRepo)
    authorisedActionForNino(controller)
  }

  trait AuthorisedTestScenario {
    val accountService            = mock[AccountService]
    val helpToSaveGetTransactions = mock[HelpToSaveGetTransactions]
    val savingsGoalRepo           = mock[SavingsGoalRepo]

    val controller: HelpToSaveController =
      new HelpToSaveController(
        logger,
        accountService,
        helpToSaveGetTransactions,
        new AlwaysAuthorisedWithIds(nino),
        config,
        savingsGoalRepo)
  }

  trait HelpToSaveMocking {
    scenario: AuthorisedTestScenario =>

    def accountReturns(stubbedResponse: Either[ErrorInfo, Option[Account]]) = {
      (accountService.account(_: Nino)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returning(Future.successful(stubbedResponse))
    }

    def savingsGoalReturns(nino: Nino, stubbedResponse: Option[SavingsGoalMongoModel]) =
      (savingsGoalRepo.get(_: Nino))
        .expects(nino)
        .returning(Future.successful(stubbedResponse))

    def helpToSaveGetTransactionsReturns(stubbedResponse: Future[Either[ErrorInfo, Option[Transactions]]]) = {
      (helpToSaveGetTransactions.getTransactions(_: Nino)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returning(stubbedResponse)
    }

    def putSavingsGoalExpects(nino: String, amount: Double) = {
      (savingsGoalRepo.put(_: SavingsGoalMongoModel))
        .expects(where { st: SavingsGoalMongoModel => st.nino == nino && st.amount == amount })
        .returning(Future.successful(()))
    }

    def deleteSavingsGoalExpects(expectedNino: Nino) = {
      (savingsGoalRepo.delete(_: Nino))
        .expects(where { suppliedNino: Nino => suppliedNino == expectedNino })
        .returning(Future.successful(()))
    }
  }
}
