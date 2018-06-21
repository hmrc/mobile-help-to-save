
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
import play.api.http.Status
import play.api.libs.json.{JsUndefined, JsValue, Json}
import play.api.libs.ws.WSResponse
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub}
import uk.gov.hmrc.mobilehelptosave.support.{MongoTestCollectionsDropAfterAll, OneServerPerSuiteWsClient, WireMockSupport}

class TransactionsISpec extends WordSpec with Matchers
  with FutureAwaits with DefaultAwaitTimeout with InvitationCleanup
  with WireMockSupport with MongoTestCollectionsDropAfterAll with OneServerPerSuiteWsClient {

  override implicit lazy val app: Application = appBuilder.build()

  private val generator = new Generator(0)
  private val nino = generator.nextNino

  "GET /savings-account/{nino}/transactions" should {

    "respond with 200 and the users transactions" in new TestData {

      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.transactionsExistForUser(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/transactions").get())
      response.status shouldBe Status.OK
      response.json shouldBe Json.parse(transactionsReturnedByMobileHelpToSaveJsonString)
    }

    "response with 200 and zero users transaction" in new TestData {

      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.zeroTransactionsExistForUser(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/transactions").get())
      response.status shouldBe Status.OK
      val jsonBody: JsValue = response.json
      (jsonBody \ "transactions" \ "operation") shouldBe a [JsUndefined]
      (jsonBody \ "transactions" \ "amount") shouldBe a [JsUndefined]
      (jsonBody \ "transactions" \ "transactionDate") shouldBe a [JsUndefined]
      (jsonBody \ "transactions" \ "accountingDate") shouldBe a [JsUndefined]
      (jsonBody \ "transactions" \ "balanceAfter") shouldBe a [JsUndefined]
      jsonBody shouldBe Json.parse(zeroTransactionsReturnedByMobileHelpToSaveJsonString)
    }

    "response with 200 and users debit transaction more than 50 pounds" in new TestData {

      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.transactionsWithDebitMoreThan50Pound(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/transactions").get())
      response.status shouldBe Status.OK
      response.json shouldBe Json.parse(transactionsWithOver50PoundDebitReturnedByMobileHelpToSaveJsonString)
    }

    "response with 200 and multiple transactions within same month and same day" in new TestData {

      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.multipleTransactionsWithinSameMonthAndDay(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/transactions").get())
      response.status shouldBe Status.OK
      response.json shouldBe Json.parse(multipleTransactionsWithinSameMonthAndDayReturnedByMobileHelpToSaveJsonString)
    }

    "respond with a 404 if the user's NINO isn't found" in new TestData {
      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.userDoesNotHaveAnHTSAccount(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/transactions").get())

      response.status shouldBe 404
      val jsonBody: JsValue = response.json
      (jsonBody \ "code").as[String] shouldBe "ACCOUNT_NOT_FOUND"
      (jsonBody \ "message").as[String] shouldBe "No Help to Save account exists for the specified NINO"
    }

    "return 401 when the user is not logged in" in {
      AuthStub.userIsNotLoggedIn()
      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 401
    }

    "return 403 when the user is logged in with an auth provider that does not provide an internalId" in {
      AuthStub.userIsLoggedInButNotWithGovernmentGatewayOrVerify()
      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 403
    }
  }
}
