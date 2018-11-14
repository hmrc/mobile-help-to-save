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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OneInstancePerTest, WordSpec}
import play.api.http.Status._
import play.api.mvc.Results
import play.api.test.Helpers.{contentAsString, status}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, Retrievals}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.support.LoggerStub

import scala.concurrent.{ExecutionContext, Future}

class AuthorisedWithIdsSpec
  extends WordSpec with Matchers
    with FutureAwaits with DefaultAwaitTimeout
    with MockFactory with OneInstancePerTest with LoggerStub with Retrievals with Results {

  private  val generator                       = new Generator(0)
  private  val testNino                        = generator.nextNino
  implicit val system      : ActorSystem       = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  "AuthorisedWithIds" should {
    "include the NINO in the request" in {
      val authConnectorStub = authConnectorStubThatWillReturn(Some(testNino.value))

      val authorised = new AuthorisedWithIdsImpl(logger, authConnectorStub)

      var capturedNino: Option[Nino] = None
      val action = authorised { request =>
        capturedNino = Some(request.nino)
        Ok
      }

      await(action(FakeRequest())) shouldBe Ok
      capturedNino shouldBe Some(testNino)
    }

    "return 403 when no NINO can be retrieved" in {
      val authConnectorStub = authConnectorStubThatWillReturn(None)

      val authorised = new AuthorisedWithIdsImpl(logger, authConnectorStub)

      val action = authorised { _ =>
        Ok
      }

      status(action(FakeRequest())) shouldBe FORBIDDEN
    }

    "return 401 when AuthConnector throws NoActiveSession" in {
      val authConnectorStub = authConnectorStubThatWillReturn(Future failed new NoActiveSession("not logged in") {})

      val authorised = new AuthorisedWithIdsImpl(logger, authConnectorStub)

      val action = authorised { _ =>
        Ok
      }

      status(action(FakeRequest())) shouldBe UNAUTHORIZED
    }

    "return 403 when AuthConnector throws any other AuthorisationException" in {
      val authConnectorStub = authConnectorStubThatWillReturn(Future failed new AuthorisationException("not authorised") {})

      val authorised = new AuthorisedWithIdsImpl(logger, authConnectorStub)

      val action = authorised { _ =>
        Ok
      }

      status(action(FakeRequest())) shouldBe FORBIDDEN
    }

    "return 403 Forbidden and log a warning when AuthConnector throws InsufficientConfidenceLevel" in {
      val authConnectorStub = authConnectorStubThatWillReturn(Future failed new InsufficientConfidenceLevel("Insufficient ConfidenceLevel") {})

      val authorised = new AuthorisedWithIdsImpl(logger, authConnectorStub)

      val action = authorised { _ =>
        Ok
      }

      val resultF = action(FakeRequest())
      status(resultF) shouldBe FORBIDDEN
      contentAsString(resultF) shouldBe "Authorisation failure [Insufficient ConfidenceLevel]"
      (slf4jLoggerStub.warn(_: String)) verify "Forbidding access due to insufficient confidence level. User will see an error screen. To fix this see NGC-3381."
    }
  }


  private def authConnectorStubThatWillReturn(nino: Option[String]): AuthConnector =
    authConnectorStubThatWillReturn(Future successful nino)

  private def authConnectorStubThatWillReturn(futureNino: Future[Option[String]]): AuthConnector = {
    val authConnectorStub = stub[AuthConnector]
    (authConnectorStub.authorise[Option[String]](_: Predicate, _: Retrieval[Option[String]])(_: HeaderCarrier, _: ExecutionContext))
      .when(ConfidenceLevel.L200, nino, *, *)
      .returns(futureNino)
    authConnectorStub
  }

}
