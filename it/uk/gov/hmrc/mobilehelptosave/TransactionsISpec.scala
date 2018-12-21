
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

import org.scalatest.{Assertion, Matchers, WordSpec}
import play.api.Application
import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSResponse
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mobilehelptosave.raml.TransactionsSchema.strictRamlTransactionsSchema
import uk.gov.hmrc.mobilehelptosave.scalatest.SchemaMatchers
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub}
import uk.gov.hmrc.mobilehelptosave.support.{ComponentSupport, OneServerPerSuiteWsClient, WireMockSupport}

class TransactionsISpec extends WordSpec with Matchers
  with SchemaMatchers with TransactionTestData
  with FutureAwaits with DefaultAwaitTimeout
  with WireMockSupport
  with OneServerPerSuiteWsClient  with ComponentSupport{

  override implicit lazy val app: Application = appBuilder.build()

  private val generator = new Generator(0)
  private val nino = generator.nextNino

  "GET /savings-account/{nino}/transactions" should {

    "respond with 200 and the users transactions" in {

      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.transactionsExistForUser(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/transactions").get())
      response.status shouldBe Status.OK
      response.json shouldBe Json.parse(transactionsReturnedByMobileHelpToSaveJsonString)
      checkTransactionsResponseInvariants(response)
    }

    "respond with 200 and an empty transactions list when there are no transactions for the NINO" in {

      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.zeroTransactionsExistForUser(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/transactions").get())
      response.status shouldBe Status.OK
      response.json shouldBe Json.parse(zeroTransactionsReturnedByMobileHelpToSaveJsonString)
      checkTransactionsResponseInvariants(response)
    }

    "respond with 200 and users debit transaction more than 50 pounds" in {

      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.transactionsWithOver50PoundDebit(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/transactions").get())
      response.status shouldBe Status.OK
      response.json shouldBe Json.parse(transactionsWithOver50PoundDebitReturnedByMobileHelpToSaveJsonString)
      checkTransactionsResponseInvariants(response)
    }

    "respond with 200 and multiple transactions within same month and same day" in {

      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.multipleTransactionsWithinSameMonthAndDay(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/transactions").get())
      response.status shouldBe Status.OK
      response.json shouldBe Json.parse(multipleTransactionsWithinSameMonthAndDayReturnedByMobileHelpToSaveJsonString)
      checkTransactionsResponseInvariants(response)
    }

    "respond with a 404 if the user's NINO isn't found" in {
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.userDoesNotHaveAnHtsAccount(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/transactions").get())

      response.status shouldBe 404
      val jsonBody: JsValue = response.json
      (jsonBody \ "code").as[String] shouldBe "ACCOUNT_NOT_FOUND"
      (jsonBody \ "message").as[String] shouldBe "No Help to Save account exists for the specified NINO"
      checkTransactionsResponseInvariants(response)
    }

    "return 401 when the user is not logged in" in {
      AuthStub.userIsNotLoggedIn()
      val response = await(wsUrl(s"/savings-account/$nino/transactions").get())
      response.status shouldBe 401
      checkTransactionsResponseInvariants(response)
      response.body shouldBe "Authorisation failure [Bearer token not supplied]"
    }

    "return 403 Forbidden when the user is logged in with an insufficient confidence level" in {
      AuthStub.userIsLoggedInWithInsufficientConfidenceLevel()
      val response = await(wsUrl(s"/savings-account/$nino/transactions").get())
      response.status shouldBe 403
      checkTransactionsResponseInvariants(response)
      response.body shouldBe "Authorisation failure [Insufficient ConfidenceLevel]"
    }
  }

  private def checkTransactionsResponseInvariants(response: WSResponse): Assertion = {
    if (response.status == Status.OK) {
      response.json should validateAgainstSchema(strictRamlTransactionsSchema)
    } else succeed
  }
}
