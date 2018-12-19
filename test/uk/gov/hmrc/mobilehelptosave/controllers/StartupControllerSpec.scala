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
import play.api.libs.json.JsObject
import play.api.test.Helpers.{contentAsJson, status}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.StartupControllerConfig
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.services.UserService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class StartupControllerSpec
  extends WordSpec
    with Matchers
    with MockFactory
    with OneInstancePerTest
    with FutureAwaits
    with DefaultAwaitTimeout {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val generator = new Generator(0)
  private val nino      = generator.nextNino

  private val mockUserService = mock[UserService]

  private val trueShuttering  = Shuttering(shuttered = true, "Shuttered", "HTS is currently not available")
  private val falseShuttering = Shuttering(shuttered = false, "", "")

  private val config = TestStartupControllerConfig(
    falseShuttering,
    supportFormEnabled = false,
    helpToSaveInfoUrl = "/info",
    helpToSaveAccessAccountUrl = "/accessAccount"
  )

  private val testUserDetails = UserDetails(UserState.NotEnrolled)

  "startup" should {
    "pass NINO obtained from auth into userService" in {
      (mockUserService.userDetails(_: Nino)(_: HeaderCarrier))
        .expects(nino, *)
        .returning(Future successful Right(testUserDetails))

      val controller = new StartupController(
        mockUserService,
        new AlwaysAuthorisedWithIds(nino),
        config)

      status(controller.startup(FakeRequest())) shouldBe 200
    }

    "check permissions using AuthorisedWithIds" in {
      val controller = new StartupController(
        mockUserService,
        NeverAuthorisedWithIds,
        config)

      status(controller.startup()(FakeRequest())) shouldBe 403
    }
  }

  "startup" when {
    "helpToSaveEnabled = true and helpToSaveShuttered = false" should {
      val controller = new StartupController(
        mockUserService,
        new AlwaysAuthorisedWithIds(nino),
        config)

      "include URLs and user in response" in {
        (mockUserService.userDetails(_: Nino)(_: HeaderCarrier))
          .expects(nino, *)
          .returning(Future successful Right(testUserDetails))

        val resultF = controller.startup(FakeRequest())
        status(resultF) shouldBe 200
        val jsonBody = contentAsJson(resultF)
        val jsonKeys = jsonBody.as[JsObject].keys
        jsonKeys should contain("user")
        (jsonBody \ "infoUrl").as[String] shouldBe "/info"
        (jsonBody \ "accessAccountUrl").as[String] shouldBe "/accessAccount"
      }

      "include shuttering information in response with shuttered = false" in {
        (mockUserService.userDetails(_: Nino)(_: HeaderCarrier))
          .expects(nino, *)
          .returning(Future successful Right(testUserDetails))

        val resultF = controller.startup(FakeRequest())
        status(resultF) shouldBe 200
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "shuttering" \ "shuttered").as[Boolean] shouldBe false
      }
    }

    "there is an error getting user details" should {
      val controller = new StartupController(
        mockUserService,
        new AlwaysAuthorisedWithIds(nino),
        config)

      "include userError and non-user fields such as URLs response" in {
        val generator = new Generator(0)
        val nino = generator.nextNino

        (mockUserService.userDetails(_: Nino)(_: HeaderCarrier))
          .expects(nino, *)
          .returning(Future successful Left(ErrorInfo.General))

        val resultF = controller.startup(FakeRequest())
        status(resultF) shouldBe 200
        val jsonBody = contentAsJson(resultF)
        val jsonKeys = jsonBody.as[JsObject].keys
        jsonKeys should not contain "user"
        (jsonBody \ "userError" \ "code").as[String] shouldBe "GENERAL"
        (jsonBody \ "infoUrl").as[String] shouldBe "/info"
        (jsonBody \ "accessAccountUrl").as[String] shouldBe "/accessAccount"
      }
    }

    "helpToSaveShuttered = true" should {
      val controller = new StartupController(
        mockUserService,
        ShouldNotBeCalledAuthorisedWithIds,
        config.copy(shuttering = trueShuttering))

      "omit URLs and user from response, and not call UserService or AuthorisedWithIds" in {
        val resultF = controller.startup(FakeRequest())
        status(resultF) shouldBe 200
        val jsonBody = contentAsJson(resultF)
        val jsonKeys = jsonBody.as[JsObject].keys
        jsonKeys should not contain "user"
        jsonKeys should not contain "infoUrl"
        jsonKeys should not contain "accessAccountUrl"
      }

      "include shuttering info in response" in {
        val resultF = controller.startup(FakeRequest())
        status(resultF) shouldBe 200
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "shuttering" \ "shuttered").as[Boolean] shouldBe true
        (jsonBody \ "shuttering" \ "title").as[String] shouldBe "Shuttered"
        (jsonBody \ "shuttering" \ "message").as[String] shouldBe "HTS is currently not available"
      }

      "continue to include feature flags because some of them take priority over shuttering" in {
        val resultF = controller.startup(FakeRequest())
        status(resultF) shouldBe 200
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "supportFormEnabled").as[Boolean] shouldBe false
      }
    }

    "helpToSaveShuttered = true and a different title and message are passed in" should {
      val controller = new StartupController(
        mockUserService,
        ShouldNotBeCalledAuthorisedWithIds,
        config.copy(
          shuttering = Shuttering(shuttered = true, "something", "some message")
        ))

      "include the passed in shuttering info in response" in {
        val resultF = controller.startup(FakeRequest())
        status(resultF) shouldBe 200
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "shuttering" \ "shuttered").as[Boolean] shouldBe true
        (jsonBody \ "shuttering" \ "title").as[String] shouldBe "something"
        (jsonBody \ "shuttering" \ "message").as[String] shouldBe "some message"
      }
    }
  }
}

case class TestStartupControllerConfig(
  shuttering: Shuttering,
  supportFormEnabled: Boolean,
  helpToSaveInfoUrl: String,
  helpToSaveAccessAccountUrl: String
) extends StartupControllerConfig
