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

import org.scalatest.{Matchers, OptionValues, WordSpec}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveProxyConnector, NsiAccount}
import uk.gov.hmrc.mobilehelptosave.domain.Account

import scala.concurrent.ExecutionContext.Implicits.{global => passedEc}
import scala.concurrent.{ExecutionContext, Future}

class AccountServiceSpec extends WordSpec with Matchers
  with FutureAwaits with DefaultAwaitTimeout with OptionValues {

  private val generator = new Generator(0)
  private val nino = generator.nextNino

  private implicit val passedHc: HeaderCarrier = HeaderCarrier()

  "account" should {
    "return account balance" in {
      val connector = fakeHelpToSaveProxyConnector(nino, Some(NsiAccount(BigDecimal("123.45"))))
      val service = new AccountServiceImpl(connector)

      await(service.account(nino)).value shouldBe Account(BigDecimal("123.45"))
    }

    "return None when the NS&I account cannot be retrieved" in {
      val connector = fakeHelpToSaveProxyConnector(nino, None)
      val service = new AccountServiceImpl(connector)

      await(service.account(nino)) shouldBe None
    }
  }

  private def fakeHelpToSaveProxyConnector(expectedNino: Nino, account: Option[NsiAccount]) = new HelpToSaveProxyConnector {
    override def nsiAccount(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[NsiAccount]] = {
      nino shouldBe expectedNino
      hc shouldBe passedHc
      ec shouldBe passedEc

      Future successful account
    }
  }
}
