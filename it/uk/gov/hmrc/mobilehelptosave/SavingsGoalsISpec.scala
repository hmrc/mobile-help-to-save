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

import org.scalatest.OptionValues
import play.api.Application
import play.api.http.Status
import play.api.libs.json.JsValue
import play.api.libs.json.Json._
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.domain.{Account, SavingsGoal}
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub, ShutteringStub}
import uk.gov.hmrc.mobilehelptosave.support.{BaseISpec, ComponentSupport, MongoSupport}
import play.api.libs.ws.writeableOf_JsValue

import scala.concurrent.ExecutionContext.Implicits.global

class SavingsGoalsISpec
    extends BaseISpec
    with MongoSupport
    with OptionValues
    with ComponentSupport
    with NumberVerification {

  override implicit lazy val app: Application = appBuilder.build()
  private val savingsGoal1          = SavingsGoal(Some(20))
  private val savingsGoalBad        = SavingsGoal(Some(20))
  private val validGoalJson         = toJson(savingsGoal1)
  private val inVaalidGoalJson      = toJson(savingsGoalBad)
  private val savingsGoal2          = SavingsGoal(goalAmount = Some(30), goalName = Some("\uD83C\uDFE1 New home"))
  private val validGoalJsonWithName = toJson(savingsGoal2)

  private def setSavingsGoal(
    nino: Nino,
    json: JsValue
  ): WSResponse =
    await(
      requestWithAuthHeaders(s"/savings-account/${nino.toString}/goals/current-goal?journeyId=$journeyId").put(json)
    )

  trait LoggedInUserScenario {
    ShutteringStub.stubForShutteringDisabled()
    HelpToSaveStub.currentUserIsEnrolled()
    HelpToSaveStub.accountExistsWithNoEmail(nino)
    AuthStub.userIsLoggedIn(nino)
    HelpToSaveStub.zeroTransactionsExistForUser(nino)
  }

  "PUT /savings-account/{nino}/goals/current-goal" should {
    "respond with 204 when putting a goal" in new LoggedInUserScenario {

      val response: WSResponse = setSavingsGoal(nino, validGoalJson)
      response.status shouldBe 204
    }

    "respond with 422 when putting a value that is not a valid monetary amount" in new LoggedInUserScenario {

      val response: WSResponse = setSavingsGoal(nino, toJson(SavingsGoal(Some(30.123))))
      response.status shouldBe Status.UNPROCESSABLE_ENTITY
      response.body.toString   should include("goal amount should be a valid monetary amount")
    }

    "respond with 422 when putting a value that is not a valid savings goal" in new LoggedInUserScenario {

      val response: WSResponse = setSavingsGoal(nino, toJson(SavingsGoal(Some(0.10))))
      response.status shouldBe Status.UNPROCESSABLE_ENTITY
      response.body.toString   should include("goal amount should be a valid monetary amount")
    }

    "respond with 422 when putting a value that is greater than the monthly savings goal" in new LoggedInUserScenario {

      val response: WSResponse = setSavingsGoal(nino, toJson(SavingsGoal(Some(51))))
      response.status shouldBe Status.UNPROCESSABLE_ENTITY
      response.body.toString   should include("goal amount should be in range 1 to 50")
    }

    "set the goal" in new LoggedInUserScenario {

      val response: WSResponse = await {
        for {
          _ <- requestWithAuthHeaders(s"/savings-account/$nino/goals/current-goal?journeyId=$journeyId")
                .put(validGoalJson)
          resp <- requestWithAuthHeaders(s"/savings-account/$nino?journeyId=$journeyId").get()
        } yield resp
      }

      response.status shouldBe 200
      val account: Account = parse(response.body).as[Account]
      account.savingsGoal.value.goalAmount shouldBe savingsGoal1.goalAmount
    }

    "update the goal when called a second time (with name)" in new LoggedInUserScenario {

      val response: WSResponse = await {
        for {
          _ <- requestWithAuthHeaders(s"/savings-account/$nino/goals/current-goal?journeyId=$journeyId")
                .put(validGoalJson)
          _ <- requestWithAuthHeaders(s"/savings-account/$nino/goals/current-goal?journeyId=$journeyId")
                .put(validGoalJsonWithName)
          resp <- requestWithAuthHeaders(s"/savings-account/$nino?journeyId=$journeyId").get()
        } yield resp
      }

      response.status shouldBe 200
      val account: Account = parse(response.body).as[Account]
      account.savingsGoal.value.goalAmount shouldBe savingsGoal2.goalAmount
      account.savingsGoal.value.goalName   shouldBe savingsGoal2.goalName
    }

    "respond with 404 and account not found when user is not enrolled" in {
      ShutteringStub.stubForShutteringDisabled()
      HelpToSaveStub.currentUserIsNotEnrolled()
      AuthStub.userIsLoggedIn(nino)

      val response: WSResponse = setSavingsGoal(nino, validGoalJson)

      (response.json \ "code").as[String]    shouldBe "ACCOUNT_NOT_FOUND"
      (response.json \ "message").as[String] shouldBe "No Help to Save account exists for the specified NINO"

      response.status shouldBe 404
    }

    "return 401 when the user is not logged in" in {
      ShutteringStub.stubForShutteringDisabled()
      AuthStub.userIsNotLoggedIn()
      val response: WSResponse = setSavingsGoal(nino, validGoalJson)
      response.status shouldBe 401
      response.body.toString   shouldBe "Authorisation failure [Bearer token not supplied]"
    }

    "return 403 Forbidden when the user is logged in with an insufficient confidence level" in {
      ShutteringStub.stubForShutteringDisabled()
      AuthStub.userIsLoggedInWithInsufficientConfidenceLevel()
      val response: WSResponse = setSavingsGoal(nino, validGoalJson)
      response.status shouldBe 403
      response.body.toString   shouldBe "Authorisation failure [Insufficient ConfidenceLevel]"
    }

    "return 400 when journeyId is not supplied" in {
      AuthStub.userIsNotLoggedIn()
      val response: WSResponse =
        await(requestWithAuthHeaders(s"/savings-account/${nino.toString}/goals/current-goal").put(validGoalJson))
      response.status shouldBe 400
    }

    "return 400 when invalid journeyId is supplied" in {
      AuthStub.userIsNotLoggedIn()
      val response: WSResponse =
        await(
          requestWithAuthHeaders(
            s"/savings-account/${nino.toString}/goals/current-goal?journeyId=ThisIsAnInvalidJourneyId"
          ).put(validGoalJson)
        )
      response.status shouldBe 400
    }
    "return 400 when neither name or amount specified" in {
      AuthStub.userIsNotLoggedIn()
      val response: WSResponse =
        await(
          requestWithAuthHeaders(
            s"/savings-account/${nino.toString}/goals/current-goal?journeyId=ThisIsAnInvalidJourneyId"
          ).put(inVaalidGoalJson)
        )
      response.status shouldBe 400
    }
  }

  "DELETE /savings-account/{nino}/goals/current-goal" should {
    "Respond with NoContent" in new LoggedInUserScenario {

      val response: WSResponse =
        await(requestWithAuthHeaders(s"/savings-account/$nino/goals/current-goal?journeyId=$journeyId").delete())
      response.status shouldBe 204
    }

    "Remove a previously set goal" in new LoggedInUserScenario {

      val response: WSResponse = await {
        for {
          _ <- requestWithAuthHeaders(s"/savings-account/$nino/goals/current-goal?journeyId=$journeyId")
                .put(validGoalJson)
          _    <- requestWithAuthHeaders(s"/savings-account/$nino/goals/current-goal?journeyId=$journeyId").delete()
          resp <- requestWithAuthHeaders(s"/savings-account/$nino?journeyId=$journeyId").get()
        } yield resp
      }

      response.status shouldBe 200
      val account: Account = parse(response.body).as[Account]
      account.savingsGoal shouldBe None
    }

    "return 400 when journeyId is not supplied" in new LoggedInUserScenario {

      val response: WSResponse = await(requestWithAuthHeaders(s"/savings-account/$nino/goals/current-goal").delete())
      response.status shouldBe 400
    }

    "return 400 when invalid NINO supplied" in new LoggedInUserScenario {

      val response: WSResponse =
        await(requestWithAuthHeaders(s"/savings-account/AA123123123/goals/current-goal?journeyId=$journeyId").delete())
      response.status shouldBe 400
    }

    "return 400 when invalid journeyId is supplied" in new LoggedInUserScenario {

      val response: WSResponse =
        await(
          requestWithAuthHeaders(s"/savings-account/$nino/goals/current-goal?journeyId=ThisIsAnInvalidJourneyId")
            .delete()
        )
      response.status shouldBe 400
    }

  }
}
