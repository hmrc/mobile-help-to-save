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

import java.util.UUID.randomUUID

import org.scalatest.{Matchers, OptionValues, WordSpec}
import play.api.http.Status
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mobilehelptosave.domain.{Account, BalanceReached, SavingsGoal, Transactions}
import uk.gov.hmrc.mobilehelptosave.scalatest.SchemaMatchers
import uk.gov.hmrc.mobilehelptosave.support.{OneServerPerSuiteWsClient, WireMockSupport}

class SandboxISpec
    extends WordSpec
    with Matchers
    with SchemaMatchers
    with OptionValues
    with TransactionTestData
    with FutureAwaits
    with DefaultAwaitTimeout
    with WireMockSupport
    with OneServerPerSuiteWsClient {

  private val sandboxRoutingHeader = "X-MOBILE-USER-ID" -> "208606423740"
  private val generator            = new Generator(0)
  private val nino                 = generator.nextNino
  private val journeyId            = randomUUID().toString

  "GET /savings-account/{nino}/transactions with sandbox header" should {
    "Return OK response containing valid Transactions JSON" in {
      val response: WSResponse =
        await(wsUrl(s"/savings-account/$nino/transactions?journeyId=$journeyId").addHttpHeaders(sandboxRoutingHeader).get())
      response.status                      shouldBe Status.OK
      response.json.validate[Transactions] shouldBe 'success
    }
    "Return 400 when journeyId not supplied" in {
      val response: WSResponse =
        await(wsUrl(s"/savings-account/$nino/transactions").addHttpHeaders(sandboxRoutingHeader).get())
      response.status shouldBe 400
    }
  }

  "GET /savings-account/{nino} with sandbox header" should {
    "Return OK response containing valid Account JSON including a savings goal" in {
      val response: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").addHttpHeaders(sandboxRoutingHeader).get())
      response.status shouldBe Status.OK
      val accountV = response.json.validate[Account]
      accountV                                          shouldBe 'success
      accountV.asOpt.value.savingsGoal.value.goalAmount shouldBe 25.0
    }
    "Return 400 when journeyId not supplied" in {
      val response: WSResponse =
        await(wsUrl(s"/savings-account/$nino").addHttpHeaders(sandboxRoutingHeader).get())
      response.status shouldBe 400
    }
  }

  "PUT /savings-account/:nino/goals/current-goal with sandbox header" should {
    "Return a No Content response" in {
      val goal = SavingsGoal(35.0)
      val response: WSResponse =
        await(wsUrl(s"/savings-account/$nino/goals/current-goal?journeyId=$journeyId").addHttpHeaders(sandboxRoutingHeader).put(Json.toJson(goal)))
      response.status shouldBe Status.NO_CONTENT
    }
    "Return 400 when journeyId not supplied" in {
      val goal = SavingsGoal(35.0)
      val response: WSResponse =
        await(wsUrl(s"/savings-account/$nino/goals/current-goal").addHttpHeaders(sandboxRoutingHeader).put(Json.toJson(goal)))
      response.status shouldBe 400
    }
  }

  "DELETE /savings-account/:nino/goals/current-goal with sandbox header" should {
    "Return a No Content response" in {
      val response: WSResponse =
        await(wsUrl(s"/savings-account/$nino/goals/current-goal?journeyId=$journeyId").addHttpHeaders(sandboxRoutingHeader).delete())
      response.status shouldBe Status.NO_CONTENT
    }
    "Return 400 when journeyId not supplied" in {
      val response: WSResponse =
        await(wsUrl(s"/savings-account/$nino/goals/current-goal").addHttpHeaders(sandboxRoutingHeader).delete())
      response.status shouldBe 400
    }
  }

  "PUT /savings-account/:nino/milestones/:milestoneType/seen with sandbox header" should {
    "Return a No Content response" in {
      val milestoneType = BalanceReached
      val response: WSResponse =
        await(wsUrl(s"/savings-account/$nino/milestones/$milestoneType/seen?journeyId=$journeyId").addHttpHeaders(sandboxRoutingHeader).put(""))
      response.status shouldBe Status.NO_CONTENT
    }
    "Return 400 when journeyId not supplied" in {
      val milestoneType = BalanceReached
      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones/$milestoneType/seen").addHttpHeaders(sandboxRoutingHeader).put(""))
      response.status shouldBe 400
    }
  }

  "GET /savings-account/:nino/milestones with sandbox header" should {
    "Return OK response containing valid milestones JSON including an achieved milestone" in {
      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").addHttpHeaders(sandboxRoutingHeader).get())
      response.status shouldBe Status.OK

      response.status                                                    shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]    shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]     shouldBe "BalanceReached1"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String]   shouldBe "You've started saving"
      (response.json \ "milestones" \ 0 \ "milestoneMessage").as[String] shouldBe "Well done for making your first payment."
    }
    "Return 400 when journeyId not supplied" in {
      val response: WSResponse =
        await(wsUrl(s"/savings-account/$nino/milestones").addHttpHeaders(sandboxRoutingHeader).get())
      response.status shouldBe 400
    }
  }
}
