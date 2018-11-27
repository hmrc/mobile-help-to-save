
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

import org.scalatest._
import play.api.Application
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mobilehelptosave.domain.{Account, SavingsGoal}
import uk.gov.hmrc.mobilehelptosave.scalatest.SchemaMatchers
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub}
import uk.gov.hmrc.mobilehelptosave.support.{OneServerPerSuiteWsClient, WireMockSupport}

import scala.concurrent.ExecutionContext.Implicits.global

class SavingsGoalsISpec
  extends WordSpec
    with Matchers
    with SchemaMatchers
    with TransactionTestData
    with FutureAwaits
    with DefaultAwaitTimeout
    with WireMockSupport
    with OptionValues
    with OneServerPerSuiteWsClient
    with NumberVerification {

  override implicit lazy val app: Application = appBuilder.build()

  private val generator = new Generator(0)
  private val nino      = generator.nextNino

  "PUT /savings-account/{nino}/goals/current-goal" should {

    val validGoalJson = Json.toJson(SavingsGoal(20))
    val savingsGoal2 = SavingsGoal(30)
    val validGoalJson2 = Json.toJson(savingsGoal2)

    "respond with 204" in {
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.accountExistsWithNoEmail(nino)
      AuthStub.userIsLoggedIn(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/goals/current-goal").put(validGoalJson))

      response.status shouldBe 204
    }

    "update the goal when called a second time" in {
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.accountExistsWithNoEmail(nino)
      AuthStub.userIsLoggedIn(nino)

      val response: WSResponse = await {
        for {
          _ <- wsUrl(s"/savings-account/$nino/goals/current-goal").put(validGoalJson)
          _ <- wsUrl(s"/savings-account/$nino/goals/current-goal").put(validGoalJson2)
          resp <- wsUrl(s"/savings-account/$nino").get()
        } yield resp
      }

      response.status shouldBe 200
      val account = Json.parse(response.body).as[Account]
      account.savingsGoal.value.goalAmount shouldBe savingsGoal2.goalAmount
    }

    "respond with 404 and account not found when user is not enrolled" in {
      HelpToSaveStub.currentUserIsNotEnrolled()
      AuthStub.userIsLoggedIn(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/goals/current-goal").put(validGoalJson))

      (response.json \ "code").as[String] shouldBe "ACCOUNT_NOT_FOUND"
      (response.json \ "message").as[String] shouldBe "No Help to Save account exists for the specified NINO"

      response.status shouldBe 404
    }

    "return 401 when the user is not logged in" in {
      AuthStub.userIsNotLoggedIn()
      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/goals/current-goal").put(validGoalJson))
      response.status shouldBe 401
      response.body shouldBe "Authorisation failure [Bearer token not supplied]"
    }

    "return 403 Forbidden when the user is logged in with an insufficient confidence level" in {
      AuthStub.userIsLoggedInWithInsufficientConfidenceLevel()
      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/goals/current-goal").put(validGoalJson))
      response.status shouldBe 403
      response.body shouldBe "Authorisation failure [Insufficient ConfidenceLevel]"
    }
  }
}
