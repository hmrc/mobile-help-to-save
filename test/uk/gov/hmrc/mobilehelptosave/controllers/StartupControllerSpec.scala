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
import org.scalatest.{Matchers, WordSpec}
import play.api.mvc.{Request, Result, Results}
import play.api.test.Helpers.status
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.domain.{InternalAuthId, UserDetails, UserState}
import uk.gov.hmrc.mobilehelptosave.services.UserService

import scala.concurrent.{ExecutionContext, Future}

class StartupControllerSpec extends WordSpec with Matchers with MockFactory with FutureAwaits with DefaultAwaitTimeout {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private class AlwaysAuthorisedWithInternalAuthId(id: InternalAuthId) extends AuthorisedWithInternalAuthId {
    override protected def refine[A](request: Request[A]): Future[Either[Result, InternalAuthIdRequest[A]]] =
      Future successful Right(new InternalAuthIdRequest(id, request))
  }

  private object NeverAuthorisedWithInternalAuthId extends AuthorisedWithInternalAuthId with Results {
    override protected def refine[A](request: Request[A]): Future[Either[Result, InternalAuthIdRequest[A]]] =
      Future successful Left(Forbidden)
  }

  "startup" should {
    "pass internalAuthId obtained from auth into userService" in {
      val internalAuthId = InternalAuthId("some-internal-auth-id")

      val mockUserService = mock[UserService]

      (mockUserService.userDetails(_: InternalAuthId)(_: HeaderCarrier, _ :ExecutionContext))
        .expects(internalAuthId, *, *)
        .returning(Future successful Some(UserDetails(UserState.Invited)))

      val controller = new StartupController(
        mockUserService,
        new AlwaysAuthorisedWithInternalAuthId(internalAuthId),
        helpToSaveEnabled = true,
        "",
        "")

      status(controller.startup(FakeRequest())) shouldBe 200
    }

    "check permissions using AuthorisedWithInternalAuthId" in {
      val mockUserService = mock[UserService]

      val controller = new StartupController(
        mockUserService,
        NeverAuthorisedWithInternalAuthId,
        helpToSaveEnabled = true,
        "",
        "")

      status(controller.startup()(FakeRequest())) shouldBe 403
    }
  }

}
