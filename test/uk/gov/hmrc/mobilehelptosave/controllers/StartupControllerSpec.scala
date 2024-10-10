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

///*
// * Copyright 2023 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.mobilehelptosave.controllers
//
//import play.api.libs.json.JsObject
//import play.api.test.Helpers._
//import play.api.test.FakeRequest
//import uk.gov.hmrc.domain.{Generator, Nino}
//import uk.gov.hmrc.http.HeaderCarrier
//import uk.gov.hmrc.mobilehelptosave.config.StartupControllerConfig
//import uk.gov.hmrc.mobilehelptosave.domain._
//import uk.gov.hmrc.mobilehelptosave.services.HtsUserService
//import uk.gov.hmrc.mobilehelptosave.support.{BaseSpec, ShutteringMocking}
//
//import scala.concurrent.ExecutionContext.Implicits.global
//import scala.concurrent.Future
//
//class StartupControllerSpec extends BaseSpec with ShutteringMocking {
//
//  private val mockUserService = mock[HtsUserService]
//
//  private val config = TestStartupControllerConfig(
//    helpToSaveInfoUrl          = "/info",
//    helpToSaveInfoUrlSso       = "/infoSso",
//    helpToSaveAccessAccountUrl = "/accessAccount",
//    helpToSaveAccountPayInUrl  = "/payIn"
//  )
//
//  private val testUserDetails = UserDetails(UserState.NotEnrolled)
//
//  "startup" should {
//    "pass NINO obtained from auth into userService" in {
//      (mockUserService
//        .userDetails(_: Nino)(_: HeaderCarrier))
//        .expects(nino, *)
//        .returning(Future successful Right(testUserDetails))
//
//      val controller =
//        new StartupController(mockUserService, new AlwaysAuthorisedWithIds(nino), config, stubControllerComponents())
//
//      status(controller.startup(FakeRequest())) shouldBe 200
//    }
//
//    "check permissions using AuthorisedWithIds" in {
//      val controller =
//        new StartupController(mockUserService, NeverAuthorisedWithIds, config, stubControllerComponents())
//
//      status(controller.startup()(FakeRequest())) shouldBe 403
//    }
//  }
//
//  "startup" when {
//    "startup helpToSaveEnabled = true and helpToSaveShuttered = false" should {
//      val controller =
//        new StartupController(mockUserService, new AlwaysAuthorisedWithIds(nino), config, stubControllerComponents())
//
//      "include URLs and user in response" in {
//        (mockUserService
//          .userDetails(_: Nino)(_: HeaderCarrier))
//          .expects(nino, *)
//          .returning(Future successful Right(testUserDetails))
//
//        val resultF = controller.startup(FakeRequest())
//        status(resultF) shouldBe 200
//        val jsonBody = contentAsJson(resultF)
//        val jsonKeys = jsonBody.as[JsObject].keys
//        jsonKeys                                   should contain("user")
//        (jsonBody \ "infoUrl").as[String]          shouldBe "/info"
//        (jsonBody \ "accessAccountUrl").as[String] shouldBe "/accessAccount"
//        (jsonBody \ "accountPayInUrl").as[String]  shouldBe "/payIn"
//      }
//
//      "include shuttering information in response with shuttered = false" in {
//        (mockUserService
//          .userDetails(_: Nino)(_: HeaderCarrier))
//          .expects(nino, *)
//          .returning(Future successful Right(testUserDetails))
//
//        val resultF = controller.startup(FakeRequest())
//        status(resultF) shouldBe 200
//        val jsonBody = contentAsJson(resultF)
//        (jsonBody \ "shuttering" \ "shuttered").as[Boolean] shouldBe false
//      }
//    }
//
//    "there is an error getting user details" should {
//      val controller =
//        new StartupController(mockUserService, new AlwaysAuthorisedWithIds(nino), config, stubControllerComponents())
//
//      "include userError and non-user fields such as URLs response" in {
//        val generator = new Generator(0)
//        val nino      = generator.nextNino
//
//        (mockUserService
//          .userDetails(_: Nino)(_: HeaderCarrier))
//          .expects(nino, *)
//          .returning(Future successful Left(ErrorInfo.General))
//
//        val resultF = controller.startup(FakeRequest())
//        status(resultF) shouldBe 200
//        val jsonBody = contentAsJson(resultF)
//        val jsonKeys = jsonBody.as[JsObject].keys
//        jsonKeys                                     should not contain "user"
//        (jsonBody \ "userError" \ "code").as[String] shouldBe "GENERAL"
//        (jsonBody \ "infoUrl").as[String]            shouldBe "/info"
//        (jsonBody \ "accessAccountUrl").as[String]   shouldBe "/accessAccount"
//        (jsonBody \ "accountPayInUrl").as[String]    shouldBe "/payIn"
//      }
//    }
//
//    "helpToSaveShuttered = true" should {
//      val controller =
//        new StartupController(mockUserService,
//                              new AlwaysAuthorisedWithIds(nino, trueShuttering),
//                              config,
//                              stubControllerComponents())
//
//      "omit URLs and user from response" in {
//        val resultF = controller.startup(FakeRequest())
//        status(resultF) shouldBe 200
//        val jsonBody = contentAsJson(resultF)
//        val jsonKeys = jsonBody.as[JsObject].keys
//        jsonKeys should not contain "user"
//        jsonKeys should not contain "infoUrl"
//        jsonKeys should not contain "accessAccountUrl"
//        jsonKeys should not contain "accountPayInUrl"
//      }
//
//      "include shuttering info in response" in {
//        val resultF = controller.startup(FakeRequest())
//        status(resultF) shouldBe 200
//        val jsonBody = contentAsJson(resultF)
//        (jsonBody \ "shuttering" \ "shuttered").as[Boolean] shouldBe true
//        (jsonBody \ "shuttering" \ "title").as[String]      shouldBe "Shuttered"
//        (jsonBody \ "shuttering" \ "message").as[String]    shouldBe "HTS is currently not available"
//      }
//
//      "continue to include feature flags because some of them take priority over shuttering" in {
//        val resultF = controller.startup(FakeRequest())
//        status(resultF) shouldBe 200
//      }
//    }
//  }
//}
//
//case class TestStartupControllerConfig(
//  helpToSaveInfoUrl:          String,
//  helpToSaveInfoUrlSso:       String,
//  helpToSaveAccessAccountUrl: String,
//  helpToSaveAccountPayInUrl:  String)
//    extends StartupControllerConfig
