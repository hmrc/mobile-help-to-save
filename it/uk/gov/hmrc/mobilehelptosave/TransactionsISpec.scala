
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

package uk.gov.hmrc.mobilehelptosave

import org.scalatest.{Matchers, WordSpec}
import play.api.Application
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSResponse
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub}
import uk.gov.hmrc.mobilehelptosave.support.{MongoTestCollectionsDropAfterAll, OneServerPerSuiteWsClient, WireMockSupport}
import play.api.http.HeaderNames.CACHE_CONTROL

class TransactionsISpec extends WordSpec with Matchers
  with FutureAwaits with DefaultAwaitTimeout with InvitationCleanup
  with WireMockSupport with MongoTestCollectionsDropAfterAll with OneServerPerSuiteWsClient {

  private val MaxAgeValue = 1001
  override implicit lazy val app: Application = appBuilder.configure(Map("helpToSave.cacheControl.maxAgeInSeconds" -> MaxAgeValue)).build()

  private val generator = new Generator(0)
  private val nino = generator.nextNino

  "GET /savings-account/{nino}/transactions" should {

    "should include a maxAge header for successful responses" in new TestData {

      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.transactionsExistForUser(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/transactions").get())
      val containsMaxAgeHeader = (headers:(String, Seq[String])) => {
        headers._1 == CACHE_CONTROL && headers._2.head == s"max-age=$MaxAgeValue"
      }

      withClue(s"$CACHE_CONTROL headers are ${response.allHeaders.find(_ == CACHE_CONTROL)}") {
        response.allHeaders.exists(containsMaxAgeHeader) shouldBe true
      }
    }

    "respond with 200 and the users transactions" in new TestData {

      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.transactionsExistForUser(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/transactions").get())
      response.status shouldBe OK
      response.json shouldBe Json.parse(transactionsReturnedByMobileHelpToSaveJsonString)
    }


    "respond with a 404 if the user's NINO isn't found" in new TestData {
      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.userDoesNotHaveAnHTSAccount(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/transactions").get())

      response.status shouldBe NOT_FOUND
      val jsonBody: JsValue = response.json
      (jsonBody \ "code").as[String] shouldBe "ACCOUNT_NOT_FOUND"
      (jsonBody \ "message").as[String] shouldBe "No Help to Save account exists for the specified NINO"
    }

    "return 401 when the user is not logged in" in {
      AuthStub.userIsNotLoggedIn()
      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe UNAUTHORIZED
    }

    "return 403 when the user is logged in with an auth provider that does not provide an internalId" in {
      AuthStub.userIsLoggedInButNotWithGovernmentGatewayOrVerify()
      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe FORBIDDEN
    }
  }
}
