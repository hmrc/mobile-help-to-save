
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
import play.api.libs.json.{JsLookupResult, JsUndefined}
import play.api.libs.ws.WSResponse
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.mobilehelptosave.domain.Transactions
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveProxyStub, HelpToSaveStub}
import uk.gov.hmrc.mobilehelptosave.support.{MongoTestCollectionsDropAfterAll, OneServerPerSuiteWsClient, WireMockSupport}

class TransactionsISpec extends WordSpec with Matchers
  with FutureAwaits with DefaultAwaitTimeout with InvitationCleanup
  with WireMockSupport with MongoTestCollectionsDropAfterAll with OneServerPerSuiteWsClient {

  override implicit lazy val app: Application = appBuilder.build()

  private val generator = new Generator(0)
  private val nino = generator.nextNino

  "GET /individuals/mobile-help-to-save/{nino}/savings-account/transactions" should {

    "respond with 200 and the users transactions" in new TestData {

      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.transactionsForUser(nino)

      val response: WSResponse = await(wsUrl(s"/individuals/mobile-help-to-save/$nino/savings-account/transactions").get())
      response.status shouldBe Status.OK
      response.json.as[Transactions] shouldBe transactions
    }


    // TODO Is this the correct behaviour for someone without and HTS account
    "respond with a 500 Internal Server Error if the users NINO isn't found" in new TestData {

      pending
      
      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.userDoesNotHaveAnHTSAccount(nino)

      val response: WSResponse = await(wsUrl(s"/individuals/mobile-help-to-save/$nino/savings-account/transactions").get())

      response.status shouldBe Status.INTERNAL_SERVER_ERROR
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

  private def shouldBeBigDecimal(jsLookupResult: JsLookupResult, expectedValue: BigDecimal): Assertion = {
    // asOpt[String] is used to check numbers are formatted like "balance": 123.45 not "balance": "123.45"
    jsLookupResult.asOpt[String] shouldBe None
    jsLookupResult.as[BigDecimal] shouldBe expectedValue
  }
}
