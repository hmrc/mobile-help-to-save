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

import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OneInstancePerTest, WordSpec}
import play.api.mvc.{Request, Result}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.TestData
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveConnectorGetTransactions
import uk.gov.hmrc.mobilehelptosave.domain.InternalAuthId

import scala.concurrent.{ExecutionContext, Future}

class TransactionControllerSpec
  extends WordSpec
    with Matchers
    with MockFactory
    with OneInstancePerTest
    with FutureAwaits
    with TestData
    with DefaultAwaitTimeout {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val generator = new Generator(0)
  private val nino = generator.nextNino
  private val internalAuthId = InternalAuthId("some-internal-auth-id")
  private val helpToSaveConnector = mock[HelpToSaveConnectorGetTransactions]

  private class AlwaysAuthorisedWithIds(id: InternalAuthId, nino: Nino) extends AuthorisedWithIds {
    override protected def refine[A](request: Request[A]): Future[Either[Result, RequestWithIds[A]]] =
      Future successful Right(new RequestWithIds(id, nino, request))
  }

  "getTransactions" should {

    "pass NINO obtained from auth into the HelpToSaveConnector" in {
      pending
      val controller = new TransactionController(helpToSaveConnector, new AlwaysAuthorisedWithIds(internalAuthId, nino))

      controller.getTransactions(nino.value)

      (helpToSaveConnector.getTransactions(_: Nino)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returning(Future successful Right(transactions))
    }
  }
}
