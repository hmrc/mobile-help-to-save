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

package uk.gov.hmrc.mobilehelptosave.services

import org.scalatest.{Matchers, WordSpec}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.AccountTestData
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveConnectorGetAccount
import uk.gov.hmrc.mobilehelptosave.domain.{Account, ErrorInfo}

import scala.concurrent.ExecutionContext.Implicits.{global => passedEc}
import scala.concurrent.{ExecutionContext, Future}

class HelpToSaveAccountServiceSpec extends WordSpec with Matchers
  with FutureAwaits with DefaultAwaitTimeout
  with AccountTestData{

  private val generator = new Generator(0)
  private val nino = generator.nextNino

  private implicit val passedHc: HeaderCarrier = HeaderCarrier()

  "account" should {
    "return the same account returned by the connector" in {
      val connector = fakeHelpToSaveConnector(nino, Right(Some(account)))
      val service = new HelpToSaveAccountService(connector)
      await(service.account(nino)) shouldBe Right(Some(account))
    }

    "return None when no account was found" in {
      val connector = fakeHelpToSaveConnector(nino, Right(None))
      val service = new HelpToSaveAccountService(connector)
      await(service.account(nino)) shouldBe Right(None)
    }

    "return errors returned by the connector" in {
      val connector = fakeHelpToSaveConnector(nino, Left(ErrorInfo.General))
      val service = new HelpToSaveAccountService(connector)
      await(service.account(nino)) shouldBe Left(ErrorInfo.General)
    }
  }

  private def fakeHelpToSaveConnector(expectedNino: Nino, accountOrError: Either[ErrorInfo, Option[Account]]): HelpToSaveConnectorGetAccount = new HelpToSaveConnectorGetAccount {
    override def getAccount(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Option[Account]]] = {
      nino shouldBe expectedNino
      hc shouldBe passedHc
      ec shouldBe passedEc

      Future successful accountOrError
    }
  }
}
