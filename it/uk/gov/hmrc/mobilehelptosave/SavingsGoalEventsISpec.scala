
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
import uk.gov.hmrc.mobilehelptosave.domain.SavingsGoal
import uk.gov.hmrc.mobilehelptosave.repository.SavingsGoalEvent
import uk.gov.hmrc.mobilehelptosave.scalatest.SchemaMatchers
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub}
import uk.gov.hmrc.mobilehelptosave.support.{MongoSupport, OneServerPerSuiteWsClient, WireMockSupport}

import scala.concurrent.ExecutionContext.Implicits.global

class SavingsGoalEventsISpec
  extends WordSpec
    with Matchers
    with SchemaMatchers
    with TransactionTestData
    with FutureAwaits
    with DefaultAwaitTimeout
    with WireMockSupport
    with MongoSupport
    with OptionValues
    with OneServerPerSuiteWsClient
    with NumberVerification {

  override implicit lazy val app: Application = appBuilder.build()

  private val generator = new Generator(0)
  private val nino      = generator.nextNino

  private val savingsGoal1   = SavingsGoal(20)
  private val validGoalJson  = Json.toJson(savingsGoal1)
  private val savingsGoal2   = SavingsGoal(30)
  private val validGoalJson2 = Json.toJson(savingsGoal2)

  "GET /savings-account/{nino}/goals/events" should {
    "return an event for each change to the goal" in {
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.accountExistsWithNoEmail(nino)
      AuthStub.userIsLoggedIn(nino)

      val response: WSResponse = await {
        for {
          _ <- wsUrl(s"/savings-account/$nino/goals/current-goal").put(validGoalJson)
          _ <- wsUrl(s"/savings-account/$nino/goals/current-goal").put(validGoalJson2)
          _ <- wsUrl(s"/savings-account/$nino/goals/current-goal").delete()

          resp <- wsUrl(s"/savings-account/$nino/goals/events").get()
        } yield resp
      }

      response.status shouldBe 200
      val events = Json.parse(response.body).as[List[SavingsGoalEvent]]
      events.length shouldBe 3
    }
  }
}
