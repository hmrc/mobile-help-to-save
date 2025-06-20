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

package uk.gov.hmrc.mobilehelptosave

import play.api.Application
import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub, ShutteringStub}
import uk.gov.hmrc.mobilehelptosave.support.{BaseISpec, ComponentSupport}

class TransactionsISpec extends BaseISpec with ComponentSupport {

  override implicit lazy val app: Application = appBuilder.build()

  "GET /savings-account/{nino}/transactions" should {

    "respond with 200 and the users transactions" in {

      ShutteringStub.stubForShutteringDisabled()
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.transactionsExistForUser(nino)

      val response: WSResponse =
        await(requestWithAuthHeaders(s"/savings-account/$nino/transactions?journeyId=$journeyId").get())
      response.status shouldBe Status.OK
      response.json   shouldBe Json.parse(transactionsReturnedByMobileHelpToSaveJsonString)
    }

    "respond with 200 and an empty transactions list when there are no transactions for the NINO" in {

      ShutteringStub.stubForShutteringDisabled()
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.zeroTransactionsExistForUser(nino)

      val response: WSResponse =
        await(requestWithAuthHeaders(s"/savings-account/$nino/transactions?journeyId=$journeyId").get())
      response.status shouldBe Status.OK
      response.json   shouldBe Json.parse(zeroTransactionsReturnedByMobileHelpToSaveJsonString)
    }

    "respond with 200 and users debit transaction more than 50 pounds" in {

      ShutteringStub.stubForShutteringDisabled()
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.transactionsWithOver50PoundDebit(nino)

      val response: WSResponse =
        await(requestWithAuthHeaders(s"/savings-account/$nino/transactions?journeyId=$journeyId").get())
      response.status shouldBe Status.OK
      response.json   shouldBe Json.parse(transactionsWithOver50PoundDebitReturnedByMobileHelpToSaveJsonString)
    }

    "respond with 200 and multiple transactions within same month and same day" in {

      ShutteringStub.stubForShutteringDisabled()
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.multipleTransactionsWithinSameMonthAndDay(nino)

      val response: WSResponse =
        await(requestWithAuthHeaders(s"/savings-account/$nino/transactions?journeyId=$journeyId").get())
      response.status shouldBe Status.OK
      response.json   shouldBe Json.parse(multipleTransactionsWithinSameMonthAndDayReturnedByMobileHelpToSaveJsonString)
    }

    "respond with a 404 if the user's NINO isn't found" in {
      ShutteringStub.stubForShutteringDisabled()
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.userDoesNotHaveAnHtsAccount(nino)

      val response: WSResponse =
        await(requestWithAuthHeaders(s"/savings-account/$nino/transactions?journeyId=$journeyId").get())

      response.status shouldBe 404
      val jsonBody: JsValue = response.json
      (jsonBody \ "code").as[String]    shouldBe "ACCOUNT_NOT_FOUND"
      (jsonBody \ "message").as[String] shouldBe "No Help to Save account exists for the specified NINO"
    }

    "respond with a 429 if the user's has made too many requests" in {
      ShutteringStub.stubForShutteringDisabled()
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.userAccountsTooManyRequests(nino)

      val response: WSResponse =
        await(requestWithAuthHeaders(s"/savings-account/$nino/transactions?journeyId=$journeyId").get())

      response.status shouldBe 429
      val jsonBody: JsValue = response.json
      (jsonBody \ "code").as[String] shouldBe "TOO_MANY_REQUESTS"
      (jsonBody \ "message")
        .as[String] shouldBe "Too many requests have been made to Help to Save. Please try again later"
    }

    "return 401 when the user is not logged in" in {
      ShutteringStub.stubForShutteringDisabled()
      AuthStub.userIsNotLoggedIn()
      val response = await(requestWithAuthHeaders(s"/savings-account/$nino/transactions?journeyId=$journeyId").get())
      response.status shouldBe 401
      response.body.toString   shouldBe "Authorisation failure [Bearer token not supplied]"
    }

    "return 403 Forbidden when the user is logged in with an insufficient confidence level" in {
      ShutteringStub.stubForShutteringDisabled()
      AuthStub.userIsLoggedInWithInsufficientConfidenceLevel()
      val response = await(requestWithAuthHeaders(s"/savings-account/$nino/transactions?journeyId=$journeyId").get())
      response.status shouldBe 403
      response.body.toString   shouldBe "Authorisation failure [Insufficient ConfidenceLevel]"
    }

    "return 400 when journeyId is not supplied" in {
      AuthStub.userIsNotLoggedIn()
      val response = await(requestWithAuthHeaders(s"/savings-account/$nino/transactions").get())
      response.status shouldBe 400
    }

    "return 400 when invalid NINO supplied" in {
      AuthStub.userIsNotLoggedIn()
      val response =
        await(requestWithAuthHeaders(s"/savings-account/AA123123123/transactions?journeyId=$journeyId").get())
      response.status shouldBe 400
    }

    "return 400 when invalid journeyId is supplied" in {
      AuthStub.userIsNotLoggedIn()
      val response =
        await(requestWithAuthHeaders(s"/savings-account/$nino/transactions?journeyId=ThisIsAnInvalidJourneyId").get())
      response.status shouldBe 400
    }
  }

}
