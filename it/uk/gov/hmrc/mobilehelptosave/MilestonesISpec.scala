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

import java.time.LocalDate
import java.util.UUID.randomUUID

import org.scalatest._
import play.api.Application
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.mobilehelptosave.scalatest.SchemaMatchers
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub}
import uk.gov.hmrc.mobilehelptosave.support.{ComponentSupport, OneServerPerSuiteWsClient, WireMockSupport}

import scala.util.Random

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

  private val generator = new Generator(Random.nextInt())
  private val journeyId = randomUUID().toString

  "GET /savings-account/:nino/milestones" should {
    "respond with 200 and empty list as JSON when there are no unseen milestones" in {
      val nino = generator.nextNino
      AuthStub.userIsLoggedIn(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                       shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").asOpt[String]    shouldBe None
      (response.json \ "milestones" \ 0 \ "milestoneKey").asOpt[String]     shouldBe None
      (response.json \ "milestones" \ 0 \ "milestoneTitle").asOpt[String]   shouldBe None
      (response.json \ "milestones" \ 0 \ "milestoneMessage").asOpt[String] shouldBe None
    }

    "respond with 200 and the BalanceReached1 milestone in a list as JSON when the milestone is hit" in {
      val nino = generator.nextNino

      loginWithBalance(0, nino)
      val accountWithZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(1, nino)
      val accountWithNonZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                    shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]    shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]     shouldBe "BalanceReached1"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String]   shouldBe "You've started saving"
      (response.json \ "milestones" \ 0 \ "milestoneMessage").as[String] shouldBe "Well done for making your first payment."
    }

    "respond with 200 and the BalanceReached100 milestone in a list as JSON when the milestone is hit" in {
      val nino = generator.nextNino

      loginWithBalance(0, nino)
      val accountWithZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(125, nino)
      val accountWithNonZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                    shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]    shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]     shouldBe "BalanceReached100"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String]   shouldBe "You have saved your first £100"
      (response.json \ "milestones" \ 0 \ "milestoneMessage").as[String] shouldBe "That's great!"
    }

    "respond with 200 and the BalanceReached200 milestone in a list as JSON when the milestone is hit" in {
      val nino = generator.nextNino

      loginWithBalance(0, nino)
      val accountWithZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(200, nino)
      val accountWithNonZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                    shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]    shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]     shouldBe "BalanceReached200"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String]   shouldBe "Well done for saving £200 so far"
      (response.json \ "milestones" \ 0 \ "milestoneMessage").as[String] shouldBe "Your savings are growing."
    }

    "respond with 200 and the BalanceReached500 milestone in a list as JSON when the milestone is hit" in {
      val nino = generator.nextNino

      loginWithBalance(0, nino)
      val accountWithZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(515, nino)
      val accountWithNonZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                    shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]    shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]     shouldBe "BalanceReached500"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String]   shouldBe "Well done"
      (response.json \ "milestones" \ 0 \ "milestoneMessage").as[String] shouldBe "You have saved £500 since opening your account."
    }

    "respond with 200 and the BalanceReached750 milestone in a list as JSON when the milestone is hit" in {
      val nino = generator.nextNino

      loginWithBalance(0, nino)
      val accountWithZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(775, nino)
      val accountWithNonZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                    shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]    shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]     shouldBe "BalanceReached750"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String]   shouldBe "You have £750 saved up in your Help to Save account so far"
      (response.json \ "milestones" \ 0 \ "milestoneMessage").as[String] shouldBe "That's great!"
    }

    "respond with 200 and the BalanceReached1000 milestone in a list as JSON when the milestone is hit" in {
      val nino = generator.nextNino

      loginWithBalance(0, nino)
      val accountWithZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(1100, nino)
      val accountWithNonZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                    shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]    shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]     shouldBe "BalanceReached1000"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String]   shouldBe "Well done for saving £1,000"
      (response.json \ "milestones" \ 0 \ "milestoneMessage").as[String] shouldBe "Your savings are growing."
    }

    "respond with 200 and the BalanceReached1500 milestone in a list as JSON when the milestone is hit" in {
      val nino = generator.nextNino

      loginWithBalance(0, nino)
      val accountWithZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(1505, nino)
      val accountWithNonZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                    shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]    shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]     shouldBe "BalanceReached1500"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String]   shouldBe "Your savings are £1,500 so far"
      (response.json \ "milestones" \ 0 \ "milestoneMessage").as[String] shouldBe "That's great!"
    }

    "respond with 200 and the BalanceReached2000 milestone in a list as JSON when the milestone is hit" in {
      val nino = generator.nextNino

      loginWithBalance(0, nino)
      val accountWithZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(2000, nino)
      val accountWithNonZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                    shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]    shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]     shouldBe "BalanceReached2000"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String]   shouldBe "Well done"
      (response.json \ "milestones" \ 0 \ "milestoneMessage").as[String] shouldBe "You have £2,000 in savings now."
    }

    "respond with 200 and the BalanceReached2400 milestone in a list as JSON when the milestone is hit" in {
      val nino = generator.nextNino

      loginWithBalance(0, nino)
      val accountWithZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(2400, nino)
      val accountWithNonZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                    shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]    shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]     shouldBe "BalanceReached2400"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String]   shouldBe "You have £2,400 in savings"
      (response.json \ "milestones" \ 0 \ "milestoneMessage").as[String] shouldBe "You have saved the most possible with Help to Save!"
    }

    "respond with 200 and an empty list when the same milestone has been hit twice and is not repeatable" in {
      val nino = generator.nextNino

      loginWithBalance(0, nino)
      val accountWithZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(1, nino)
      val accountWithNonZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())
      val markedAsSeen:              WSResponse = await(wsUrl(s"/savings-account/$nino/milestones/BalanceReached/seen?journeyId=$journeyId").put(""))

      loginWithBalance(0, nino)
      val accountWithZeroBalanceAgain: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(1, nino)
      val accountWithNonZeroBalanceAgain: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                       shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").asOpt[String]    shouldBe None
      (response.json \ "milestones" \ 0 \ "milestoneKey").asOpt[String]     shouldBe None
      (response.json \ "milestones" \ 0 \ "milestoneTitle").asOpt[String]   shouldBe None
      (response.json \ "milestones" \ 0 \ "milestoneMessage").asOpt[String] shouldBe None
    }

    "respond with 200 and the same milestone after it has been hit once before if it is repeatable" in {
      val nino = generator.nextNino

      loginWithBalance(0, nino)
      val accountWithZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(250, nino)
      val accountWithNonZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())
      val markedAsSeen:              WSResponse = await(wsUrl(s"/savings-account/$nino/milestones/BalanceReached/seen?journeyId=$journeyId").put(""))

      loginWithBalance(0, nino)
      val accountWithZeroBalanceAgain: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(270, nino)
      val accountWithNonZeroBalanceAgain: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                    shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]    shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]     shouldBe "BalanceReached200"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String]   shouldBe "Well done for saving £200 so far"
      (response.json \ "milestones" \ 0 \ "milestoneMessage").as[String] shouldBe "Your savings are growing."
    }

    "respond with 200 and only the most recent milestone of a specific type in a list as JSON" in {
      val nino = generator.nextNino

      loginWithBalance(0, nino)
      val accountWithZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(1, nino)
      val accountWithStartedSavingBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(100, nino)
      val accountWith100Balance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                    shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]    shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]     shouldBe "BalanceReached100"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String]   shouldBe "You have saved your first £100"
      (response.json \ "milestones" \ 0 \ "milestoneMessage").as[String] shouldBe "That's great!"

      (response.json \ "milestones" \ 1 \ "milestoneType").asOpt[String]    shouldBe None
      (response.json \ "milestones" \ 1 \ "milestoneKey").asOpt[String]     shouldBe None
      (response.json \ "milestones" \ 1 \ "milestoneTitle").asOpt[String]   shouldBe None
      (response.json \ "milestones" \ 1 \ "milestoneMessage").asOpt[String] shouldBe None
    }

    "respond with 200 and the EndOfFirstBonusPeriodPositiveBonus milestone in a list as JSON when the milestone is hit" in {
      val nino = generator.nextNino

      loginWithBalanceAndBonusTerms(10, nino, 200, 0, LocalDate.now().plusDays(1), 400, LocalDate.now().plusYears(2))
      val accountWithNonZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                  shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]  shouldBe "BonusPeriod"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]   shouldBe "EndOfFirstBonusPeriodPositiveBonus"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String] shouldBe "It's nearly the end of year 2"
      (response.json \ "milestones" \ 0 \ "milestoneMessage")
        .as[String] shouldBe s"Your first bonus of £200 will be paid into your bank account from 2020-01-01."
    }

    "return 400 when journeyId is not supplied" in {
      val nino = generator.nextNino
      AuthStub.userIsLoggedIn(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones").get())

      response.status shouldBe 400
    }
  }

  "PUT /savings-account/:nino/milestones/:milestoneType/seen" should {
    "mark milestones of a certain type as seen using the nino and milestone type" in {
      val nino = generator.nextNino

      loginWithBalance(0, nino)
      val accountWithZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(1, nino)
      val accountWithNonZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response:   WSResponse = await(wsUrl(s"/savings-account/$nino/milestones/BalanceReached/seen?journeyId=$journeyId").put(""))
      val milestones: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status shouldBe 204

      milestones.status                                                       shouldBe 200
      (milestones.json \ "milestones" \ 0 \ "milestoneType").asOpt[String]    shouldBe None
      (milestones.json \ "milestones" \ 0 \ "milestoneKey").asOpt[String]     shouldBe None
      (milestones.json \ "milestones" \ 0 \ "milestoneTitle").asOpt[String]   shouldBe None
      (milestones.json \ "milestones" \ 0 \ "milestoneMessage").asOpt[String] shouldBe None
    }

    "mark unrepeatable milestones as seen and response comes back as an empty list" in {
      val nino = generator.nextNino

      loginWithBalance(0, nino)
      val accountWithZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(1, nino)
      val accountWithNonZeroBalance: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response:   WSResponse = await(wsUrl(s"/savings-account/$nino/milestones/BalanceReached/seen?journeyId=$journeyId").put(""))
      val milestones: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      loginWithBalance(0, nino)
      val accountWithZeroBalanceAgain: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(1, nino)
      val accountWithNonZeroBalanceAgain: WSResponse = await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val milestonesAgain: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())
      milestonesAgain.status                                                       shouldBe 200
      (milestonesAgain.json \ "milestones" \ 0 \ "milestoneType").asOpt[String]    shouldBe None
      (milestonesAgain.json \ "milestones" \ 0 \ "milestoneKey").asOpt[String]     shouldBe None
      (milestonesAgain.json \ "milestones" \ 0 \ "milestoneTitle").asOpt[String]   shouldBe None
      (milestonesAgain.json \ "milestones" \ 0 \ "milestoneMessage").asOpt[String] shouldBe None
    }

    "return 400 when journeyId is not supplied" in {
      val nino = generator.nextNino
      AuthStub.userIsLoggedIn(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones/BalanceReached/seen").put(""))
      response.status shouldBe 400
    }
  }

  private def loginWithBalance(balance: BigDecimal, nino: Nino) = {
    wireMockServer.resetAll()
    AuthStub.userIsLoggedIn(nino)
    HelpToSaveStub.currentUserIsEnrolled()
    HelpToSaveStub.accountExists(balance, nino)
  }

  private def loginWithBalanceAndBonusTerms(
    balance:                   BigDecimal,
    nino:                      Nino,
    firstPeriodBonusEstimate:  BigDecimal,
    firstPeriodBonusPaid:      BigDecimal,
    firstPeriodEndDate:        LocalDate,
    secondPeriodBonusEstimate: BigDecimal,
    secondPeriodEndDate:       LocalDate) = {
    wireMockServer.resetAll()
    AuthStub.userIsLoggedIn(nino)
    HelpToSaveStub.currentUserIsEnrolled()
    HelpToSaveStub.accountExistsSpecifyBonusTerms(
      balance,
      nino,
      firstPeriodBonusEstimate,
      firstPeriodBonusPaid,
      firstPeriodEndDate,
      secondPeriodBonusEstimate,
      secondPeriodEndDate)
  }
}
