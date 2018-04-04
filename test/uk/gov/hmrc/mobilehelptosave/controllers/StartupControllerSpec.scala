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
import play.api.libs.json.JsObject
import play.api.mvc.{Request, Result, Results}
import play.api.test.Helpers.{contentAsJson, status}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.domain.{InternalAuthId, UserDetails, UserState}
import uk.gov.hmrc.mobilehelptosave.services.UserService

import scala.concurrent.{ExecutionContext, Future}

class StartupControllerSpec extends WordSpec with Matchers with MockFactory with FutureAwaits with DefaultAwaitTimeout {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val generator = new Generator(0)
  private val nino = generator.nextNino

  private class AlwaysAuthorisedWithIds(id: InternalAuthId, nino: Nino) extends AuthorisedWithIds {
    override protected def refine[A](request: Request[A]): Future[Either[Result, RequestWithIds[A]]] =
      Future successful Right(new RequestWithIds(id, nino, request))
  }

  private object NeverAuthorisedWithIds extends AuthorisedWithIds with Results {
    override protected def refine[A](request: Request[A]): Future[Either[Result, RequestWithIds[A]]] =
      Future successful Left(Forbidden)
  }

  "startup" should {
    "pass internalAuthId and NINO obtained from auth into userService" in {
      val internalAuthId = InternalAuthId("some-internal-auth-id")

      val mockUserService = mock[UserService]

      (mockUserService.userDetails(_: InternalAuthId, _: Nino)(_: HeaderCarrier, _: ExecutionContext))
        .expects(internalAuthId, nino, *, *)
        .returning(Future successful Some(UserDetails(UserState.Invited, None)))

      val controller = new StartupController(
        mockUserService,
        new AlwaysAuthorisedWithIds(internalAuthId, nino),
        helpToSaveShuttered = false,
        helpToSaveEnabled = true,
        balanceEnabled = false,
        paidInThisMonthEnabled = false,
        firstBonusEnabled = false,
        shareInvitationEnabled = false,
        savingRemindersEnabled = false,
        "",
        "",
        "")

      status(controller.startup(FakeRequest())) shouldBe 200
    }

    "include URLs and user in response when helpToSaveEnabled = true" in {
      val internalAuthId = InternalAuthId("some-internal-auth-id")
      val generator = new Generator(0)
      val nino = generator.nextNino

      val mockUserService = mock[UserService]

      (mockUserService.userDetails(_: InternalAuthId, _: Nino)(_: HeaderCarrier, _: ExecutionContext))
        .expects(internalAuthId, nino, *, *)
        .returning(Future successful Some(UserDetails(UserState.Invited, None)))

      val controller = new StartupController(
        mockUserService,
        new AlwaysAuthorisedWithIds(internalAuthId, nino),
        helpToSaveShuttered = false,
        helpToSaveEnabled = true,
        balanceEnabled = false,
        paidInThisMonthEnabled = false,
        firstBonusEnabled = false,
        shareInvitationEnabled = false,
        savingRemindersEnabled = false,
        helpToSaveInfoUrl = "/info",
        helpToSaveInvitationUrl = "/invitation",
        helpToSaveAccessAccountUrl = "/accessAccount")

      val resultF = controller.startup(FakeRequest())
      status(resultF) shouldBe 200
      val jsonBody = contentAsJson(resultF)
      val jsonKeys = jsonBody.as[JsObject].keys
      jsonKeys should contain("user")
      (jsonBody \ "infoUrl").as[String] shouldBe "/info"
      (jsonBody \ "invitationUrl").as[String] shouldBe "/invitation"
      (jsonBody \ "accessAccountUrl").as[String] shouldBe "/accessAccount"
    }

    "omit URLs and user from response when helpToSaveEnabled = false" in {
      val internalAuthId = InternalAuthId("some-internal-auth-id")

      val mockUserService = mock[UserService]

      val controller = new StartupController(
        mockUserService,
        new AlwaysAuthorisedWithIds(internalAuthId, nino),
        helpToSaveShuttered = false,
        helpToSaveEnabled = false,
        balanceEnabled = false,
        paidInThisMonthEnabled = false,
        firstBonusEnabled = false,
        shareInvitationEnabled = false,
        savingRemindersEnabled = false,
        helpToSaveInfoUrl = "/info",
        helpToSaveInvitationUrl = "/invitation",
        helpToSaveAccessAccountUrl = "/accessAccount")

      val resultF = controller.startup(FakeRequest())
      status(resultF) shouldBe 200
      val jsonBody = contentAsJson(resultF)
      val jsonKeys = jsonBody.as[JsObject].keys
      jsonKeys should not contain "user"
      jsonKeys should not contain "infoUrl"
      jsonKeys should not contain "invitationUrl"
      jsonKeys should not contain "accessAccountUrl"
    }

    "omit URLs and user from response when helpToSaveShuttered = true" in {
      val internalAuthId = InternalAuthId("some-internal-auth-id")

      val mockUserService = mock[UserService]

      val controller = new StartupController(
        mockUserService,
        new AlwaysAuthorisedWithIds(internalAuthId, nino),
        helpToSaveShuttered = true,
        helpToSaveEnabled = true,
        balanceEnabled = false,
        paidInThisMonthEnabled = false,
        firstBonusEnabled = false,
        shareInvitationEnabled = false,
        savingRemindersEnabled = false,
        helpToSaveInfoUrl = "/info",
        helpToSaveInvitationUrl = "/invitation",
        helpToSaveAccessAccountUrl = "/accessAccount")

      val resultF = controller.startup(FakeRequest())
      status(resultF) shouldBe 200
      val jsonBody = contentAsJson(resultF)
      val jsonKeys = jsonBody.as[JsObject].keys
      jsonKeys should not contain "user"
      jsonKeys should not contain "infoUrl"
      jsonKeys should not contain "invitationUrl"
      jsonKeys should not contain "accessAccountUrl"
    }

    "continue to include feature flags when helpToSaveShuttered = true because some of them take priority over shuttering" in {
      val internalAuthId = InternalAuthId("some-internal-auth-id")

      val mockUserService = mock[UserService]

      val controller = new StartupController(
        mockUserService,
        new AlwaysAuthorisedWithIds(internalAuthId, nino),
        helpToSaveShuttered = true,
        helpToSaveEnabled = true,
        balanceEnabled = false,
        paidInThisMonthEnabled = true,
        firstBonusEnabled = false,
        shareInvitationEnabled = false,
        savingRemindersEnabled = false,
        helpToSaveInfoUrl = "/info",
        helpToSaveInvitationUrl = "/invitation",
        helpToSaveAccessAccountUrl = "/accessAccount")

      val resultF = controller.startup(FakeRequest())
      status(resultF) shouldBe 200
      val jsonBody = contentAsJson(resultF)
      (jsonBody \ "enabled").as[Boolean] shouldBe true
      (jsonBody \ "balanceEnabled").as[Boolean] shouldBe false
      (jsonBody \ "paidInThisMonthEnabled").as[Boolean] shouldBe true
      (jsonBody \ "firstBonusEnabled").as[Boolean] shouldBe false
      (jsonBody \ "shareInvitationEnabled").as[Boolean] shouldBe false
      (jsonBody \ "savingRemindersEnabled").as[Boolean] shouldBe false
    }

    "check permissions using AuthorisedWithIds" in {
      val mockUserService = mock[UserService]

      val controller = new StartupController(
        mockUserService,
        NeverAuthorisedWithIds,
        helpToSaveShuttered = false,
        helpToSaveEnabled = true,
        balanceEnabled = false,
        paidInThisMonthEnabled = false,
        firstBonusEnabled = false,
        shareInvitationEnabled = false,
        savingRemindersEnabled = false,
        "",
        "",
        "")

      status(controller.startup()(FakeRequest())) shouldBe 403
    }
  }

}
