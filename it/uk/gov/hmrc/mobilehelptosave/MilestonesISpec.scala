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

import org.scalatest._
import play.api.Application
import play.api.libs.ws.WSResponse
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mobilehelptosave.scalatest.SchemaMatchers
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub}
import uk.gov.hmrc.mobilehelptosave.support.{ComponentSupport, OneServerPerSuiteWsClient, WireMockSupport}

class MilestonesISpec
    extends WordSpec
    with Matchers
    with SchemaMatchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with WireMockSupport
    with OneServerPerSuiteWsClient
    with NumberVerification
    with ComponentSupport {

  override implicit lazy val app: Application = appBuilder.build()

  private val generator = new Generator(0)
  private val journeyId = randomUUID().toString
  
  "GET /savings-account/:nino/milestones" should {
    val nino = generator.nextNino

    "respond with 200 and empty list as JSON when there are no unseen milestones" in {
      AuthStub.userIsLoggedIn(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status shouldBe 200
    }

    "respond with 200 and the BalanceReached milestone in a list as JSON when milestones have been hit" in {
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.accountExistsWithZeroBalance(nino)

      val accountWithZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      wireMockServer.resetAll()

      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.accountExists(nino)

      val accountWithNonZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status shouldBe 200

      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]    shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String]   shouldBe "You've started saving"
      (response.json \ "milestones" \ 0 \ "milestoneMessage").as[String] shouldBe "Well done for making your first payment."
    }
  }

  "PUT /savings-account/:nino/milestones/:milestoneType/seen" should {
    "mark milestones of a certain type as seen using the nino and milestone type" in {
      val nino = generator.nextNino

      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.accountExistsWithZeroBalance(nino)

      val accountWithZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      wireMockServer.resetAll()

      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.accountExists(nino)

      val accountWithNonZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      wireMockServer.resetAll()

      AuthStub.userIsLoggedIn(nino)

      val response:   WSResponse = await(wsUrl(s"/savings-account/$nino/milestones/BalanceReached/seen?journeyId=$journeyId").put(""))
      val milestones: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status shouldBe 204

      milestones.status                                                       shouldBe 200
      (milestones.json \ "milestones" \ 0 \ "milestoneType").asOpt[String]    shouldBe None
      (milestones.json \ "milestones" \ 0 \ "milestoneTitle").asOpt[String]   shouldBe None
      (milestones.json \ "milestones" \ 0 \ "milestoneMessage").asOpt[String] shouldBe None
    }
  }

}
