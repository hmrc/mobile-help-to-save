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
import play.api.mvc.Results
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, Verify}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, Retrievals, ~}
import uk.gov.hmrc.auth.core.syntax.retrieved._
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.domain.InternalAuthId
import uk.gov.hmrc.play.test.UnitSpec
import play.api.http.Status._

import scala.concurrent.{ExecutionContext, Future}

class AuthorisedWithIdsSpec extends UnitSpec with MockFactory with Retrievals with Results {

  private val generator = new Generator(0)
  private val testNino = generator.nextNino
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  "AuthorisedWithIds" should {
    "include the internal auth ID and NINO in the request" in {
      val authConnectorStub = authConnectorStubThatWillReturn(Some("some-internal-auth-id"), Some(testNino.value))

      val authorised = new AuthorisedWithIdsImpl(authConnectorStub)

      var capturedInternalAuthId: Option[InternalAuthId] = None
      var capturedNino: Option[Nino] = None
      val action = authorised { request =>
        capturedInternalAuthId = Some(request.internalAuthId)
        capturedNino = Some(request.nino)
        Ok
      }

      await(action(FakeRequest())) shouldBe Ok
      capturedInternalAuthId shouldBe Some(InternalAuthId("some-internal-auth-id"))
      capturedNino shouldBe Some(testNino)
    }

    "return 500 when no internal auth ID can be retrieved" in {
      val authConnectorStub = authConnectorStubThatWillReturn(None, Some(testNino.value))

      val authorised = new AuthorisedWithIdsImpl(authConnectorStub)

      val action = authorised { _ =>
        Ok
      }

      status(action(FakeRequest())) shouldBe INTERNAL_SERVER_ERROR
    }

    "return 403 when no NINO can be retrieved" in {
      val authConnectorStub = authConnectorStubThatWillReturn(Some("some-internal-auth-id"), None)

      val authorised = new AuthorisedWithIdsImpl(authConnectorStub)

      val action = authorised { _ =>
        Ok
      }

      status(action(FakeRequest())) shouldBe FORBIDDEN
    }

    "return 401 when AuthConnector throws NoActiveSession" in {
      val authConnectorStub = authConnectorStubThatWillReturn(Future failed new NoActiveSession("not logged in") {})

      val authorised = new AuthorisedWithIdsImpl(authConnectorStub)

      val action = authorised { _ =>
        Ok
      }

      status(action(FakeRequest())) shouldBe UNAUTHORIZED
    }

    "return 403 when AuthConnector throws any other AuthorisationException" in {
      val authConnectorStub = authConnectorStubThatWillReturn(Future failed new AuthorisationException("not authorised") {})

      val authorised = new AuthorisedWithIdsImpl(authConnectorStub)

      val action = authorised { _ =>
        Ok
      }

      status(action(FakeRequest())) shouldBe FORBIDDEN
    }

    "return 403 Forbidden when AuthConnector throws InsufficientConfidenceLevel" in {
      val authConnectorStub = authConnectorStubThatWillReturn(Future failed new InsufficientConfidenceLevel("Insufficient ConfidenceLevel") {})

      val authorised = new AuthorisedWithIdsImpl(authConnectorStub)

      val action = authorised { _ =>
        Ok
      }

      val result = await(action(FakeRequest()))
      status(result) shouldBe FORBIDDEN
      bodyOf(result) shouldBe "Authorisation failure [Insufficient ConfidenceLevel]"
    }
  }


  private def authConnectorStubThatWillReturn(internalAuthId: Option[String], nino: Option[String]): AuthConnector =
    authConnectorStubThatWillReturn(Future successful (internalAuthId and nino))

  private def authConnectorStubThatWillReturn(futureIds: Future[Option[String] ~ Option[String]]): AuthConnector = {
    val authConnectorStub = stub[AuthConnector]
    (authConnectorStub.authorise[Option[String] ~ Option[String]](_: Predicate, _: Retrieval[Option[String] ~ Option[String]])(_: HeaderCarrier, _: ExecutionContext))
      .when(AuthProviders(GovernmentGateway, Verify) and ConfidenceLevel.L200, internalId and nino, *, *)
      .returns(futureIds)
    authConnectorStub
  }

}
