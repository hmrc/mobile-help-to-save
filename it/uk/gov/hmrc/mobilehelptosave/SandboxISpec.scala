
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
import play.api.libs.ws.WSResponse
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mobilehelptosave.domain.{Account, Transactions}
import uk.gov.hmrc.mobilehelptosave.scalatest.SchemaMatchers
import uk.gov.hmrc.mobilehelptosave.support.{OneServerPerSuiteWsClient, WireMockSupport}

class SandboxISpec extends WordSpec with Matchers
  with SchemaMatchers with TransactionTestData
  with FutureAwaits with DefaultAwaitTimeout
  with WireMockSupport
  with OneServerPerSuiteWsClient {

  override implicit lazy val app: Application = appBuilder.build()

  private val sandboxRoutingHeader = "X-MOBILE-USER-ID" -> "208606423740"
  private val generator = new Generator(0)
  private val nino = generator.nextNino

  "GET /savings-account/{nino}/transactions with sandbox header" should {
    "Return OK response containing valid Transactions JSON" in {
      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/transactions").withHeaders(sandboxRoutingHeader).get())
      response.status shouldBe Status.OK
      response.json.validate[Transactions] shouldBe 'success
    }
  }

  "GET /savings-account/{nino} with sandbox header" should {
    "Return OK response containing valid Account JSON" in {
      val response: WSResponse = await(wsUrl(s"/savings-account/$nino").withHeaders(sandboxRoutingHeader).get())
      response.status shouldBe Status.OK
      response.json.validate[Account] shouldBe 'success
    }
  }
}
