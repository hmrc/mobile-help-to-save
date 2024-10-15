/*
 * Copyright 2024 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.libs.json.JsObject
import play.api.test.Helpers._
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.StartupControllerConfig
import uk.gov.hmrc.mobilehelptosave.connectors.HttpClientV2Helper
import uk.gov.hmrc.mobilehelptosave.support.ShutteringMocking
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.services.HtsUserService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.never.onComplete
import scala.util.{Failure, Success}

class StartupControllerSpec extends HttpClientV2Helper with ShutteringMocking{

  private val mockUserService = mock[HtsUserService]


  private val config = TestStartupControllerConfig(
    helpToSaveInfoUrl          = "/info",
    helpToSaveInfoUrlSso       = "/infoSso",
    helpToSaveAccessAccountUrl = "/accessAccount",
    helpToSaveAccountPayInUrl  = "/payIn"
  )

  private val testUserDetails = UserDetails(UserState.NotEnrolled)
  "startup" should {
    "pass NINO obtained from auth into userService" in {
      when(mockUserService
          .userDetails(any[Nino])(any[HeaderCarrier]))
          .thenReturn(Future successful Right(testUserDetails))
      onComplete {
        case Success(_) => Right(testUserDetails)
        case Failure(_) =>
      }
    }


    "check permissions using AuthorisedWithIds" in {
      val controller =
        new StartupController(mockUserService, NeverAuthorisedWithIds, config, stubControllerComponents())

      status(controller.startup()(FakeRequest())) mustBe 403
    }
  }

  "startup" when {
    "startup helpToSaveEnabled = true and helpToSaveShuttered = false" should {
      val controller =
        new StartupController(mockUserService, new AlwaysAuthorisedWithIds(nino), config, stubControllerComponents())

      "include URLs and user in response" in {
        when(mockUserService.userDetails(any[Nino])(any[HeaderCarrier]))
          .thenReturn(Future successful Right(testUserDetails))
        onComplete {
          case Success(_) => Right(testUserDetails)
          case Failure(_) =>
        }


        val resultF = controller.startup(FakeRequest())
        status(resultF) mustBe 200
        val jsonBody = contentAsJson(resultF)
        val jsonKeys = jsonBody.as[JsObject].keys
        jsonKeys                                   must  contain("user")
        (jsonBody \ "infoUrl").as[String]          mustBe "/info"
        (jsonBody \ "accessAccountUrl").as[String] mustBe "/accessAccount"
        (jsonBody \ "accountPayInUrl").as[String]  mustBe "/payIn"
      }

      "include shuttering information in response with shuttered = false" in {
        when(mockUserService.userDetails(any[Nino])(any[HeaderCarrier]))
            .thenReturn(Future successful Right(testUserDetails))
            onComplete {
            case Success(_) => Right(testUserDetails)
            case Failure(_) =>
          }

        val resultF = controller.startup(FakeRequest())
        status(resultF) mustBe 200
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "shuttering" \ "shuttered").as[Boolean] mustBe false
      }
    }

    "there is an error getting user details" should {
      val controller =
        new StartupController(mockUserService, new AlwaysAuthorisedWithIds(nino), config, stubControllerComponents())

      "include userError and non-user fields such as URLs response" in {
        val generator = new Generator(0)
        val nino      = generator.nextNino

        when(mockUserService.userDetails(any[Nino])(any[HeaderCarrier]))
          .thenReturn(Future successful Left(ErrorInfo.General))
        onComplete {
          case Success(_) => Left(ErrorInfo.General)
          case Failure(_) =>
        }

        val resultF = controller.startup(FakeRequest())
        status(resultF) mustBe 200
        val jsonBody = contentAsJson(resultF)
        val jsonKeys = jsonBody.as[JsObject].keys
        jsonKeys                                     must not contain "user"
        (jsonBody \ "userError" \ "code").as[String] mustBe "GENERAL"
        (jsonBody \ "infoUrl").as[String]            mustBe "/info"
        (jsonBody \ "accessAccountUrl").as[String]   mustBe "/accessAccount"
        (jsonBody \ "accountPayInUrl").as[String]    mustBe "/payIn"
      }
    }

    "helpToSaveShuttered = true" should {
      val controller =
        new StartupController(mockUserService,
                              new AlwaysAuthorisedWithIds(nino, trueShuttering),
                              config,
                              stubControllerComponents())

      "omit URLs and user from response" in {
        val resultF = controller.startup(FakeRequest())
        status(resultF) mustBe 200
        val jsonBody = contentAsJson(resultF)
        val jsonKeys = jsonBody.as[JsObject].keys
        jsonKeys must not contain "user"
        jsonKeys must not contain "infoUrl"
        jsonKeys must not contain "accessAccountUrl"
        jsonKeys must not contain "accountPayInUrl"
      }

      "include shuttering info in response" in {
        val resultF = controller.startup(FakeRequest())
        status(resultF) mustBe 200
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "shuttering" \ "shuttered").as[Boolean] mustBe true
        (jsonBody \ "shuttering" \ "title").as[String]      mustBe "Shuttered"
        (jsonBody \ "shuttering" \ "message").as[String]    mustBe "HTS is currently not available"
      }

      "continue to include feature flags because some of them take priority over shuttering" in {
        val resultF = controller.startup(FakeRequest())
        status(resultF) mustBe 200
      }
    }
  }
}

case class TestStartupControllerConfig(
  helpToSaveInfoUrl:          String,
  helpToSaveInfoUrlSso:       String,
  helpToSaveAccessAccountUrl: String,
  helpToSaveAccountPayInUrl:  String)
    extends StartupControllerConfig
