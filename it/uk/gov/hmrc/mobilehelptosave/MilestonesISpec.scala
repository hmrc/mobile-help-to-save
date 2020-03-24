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
import java.time.format.DateTimeFormatter
import java.util.UUID.randomUUID

import org.scalatest._
import play.api.Application
import play.api.libs.ws.WSResponse
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.mobilehelptosave.scalatest.SchemaMatchers
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub, ShutteringStub}
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

  private val generator  = new Generator(Random.nextInt())
  private val journeyId  = randomUUID().toString
  private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy")

  "GET /savings-account/:nino/milestones" should {
    "respond with 200 and empty list as JSON when there are no unseen milestones" in {
      val nino = generator.nextNino
      AuthStub.userIsLoggedIn(nino)
      ShutteringStub.stubForShutteringDisabled()

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                       shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").asOpt[String]    shouldBe None
      (response.json \ "milestones" \ 0 \ "milestoneKey").asOpt[String]     shouldBe None
      (response.json \ "milestones" \ 0 \ "milestoneTitle").asOpt[String]   shouldBe None
      (response.json \ "milestones" \ 0 \ "milestoneMessage").asOpt[String] shouldBe None
    }

    "respond with 200 and the BalanceReached1 milestone in a list as JSON when the milestone is hit" in {
      val nino = generator.nextNino
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalance(0, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(1, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                  shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]  shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]   shouldBe "BalanceReached1"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String] shouldBe "You've started saving"
      (response.json \ "milestones" \ 0 \ "milestoneMessage")
        .as[String] shouldBe "Well done for making your first payment."
    }

    "respond with 200 and the BalanceReached100 milestone in a list as JSON when the milestone is hit" in {
      val nino = generator.nextNino
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalance(0, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(125, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                    shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]    shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]     shouldBe "BalanceReached100"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String]   shouldBe "You have saved your first £100"
      (response.json \ "milestones" \ 0 \ "milestoneMessage").as[String] shouldBe "That's great!"
    }

    "respond with 200 and the BalanceReached200 milestone in a list as JSON when the milestone is hit" in {
      val nino = generator.nextNino
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalance(0, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(200, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                    shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]    shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]     shouldBe "BalanceReached200"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String]   shouldBe "Well done for saving £200 so far"
      (response.json \ "milestones" \ 0 \ "milestoneMessage").as[String] shouldBe "Your savings are growing."
    }

    "respond with 200 and the BalanceReached500 milestone in a list as JSON when the milestone is hit" in {
      val nino = generator.nextNino
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalance(0, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(515, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                  shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]  shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]   shouldBe "BalanceReached500"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String] shouldBe "Well done"
      (response.json \ "milestones" \ 0 \ "milestoneMessage")
        .as[String] shouldBe "You have saved £500 since opening your account."
    }

    "respond with 200 and the BalanceReached750 milestone in a list as JSON when the milestone is hit" in {
      val nino = generator.nextNino
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalance(0, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(775, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                 shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String] shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]  shouldBe "BalanceReached750"
      (response.json \ "milestones" \ 0 \ "milestoneTitle")
        .as[String]                                                      shouldBe "You have £750 saved up in your Help to Save account so far"
      (response.json \ "milestones" \ 0 \ "milestoneMessage").as[String] shouldBe "That's great!"
    }

    "respond with 200 and the BalanceReached1000 milestone in a list as JSON when the milestone is hit" in {
      val nino = generator.nextNino
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalance(0, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(1100, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                    shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]    shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]     shouldBe "BalanceReached1000"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String]   shouldBe "Well done for saving £1,000"
      (response.json \ "milestones" \ 0 \ "milestoneMessage").as[String] shouldBe "Your savings are growing."
    }

    "respond with 200 and the BalanceReached1500 milestone in a list as JSON when the milestone is hit" in {
      val nino = generator.nextNino
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalance(0, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(1505, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                    shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]    shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]     shouldBe "BalanceReached1500"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String]   shouldBe "Your savings are £1,500 so far"
      (response.json \ "milestones" \ 0 \ "milestoneMessage").as[String] shouldBe "That's great!"
    }

    "respond with 200 and the BalanceReached2000 milestone in a list as JSON when the milestone is hit" in {
      val nino = generator.nextNino
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalance(0, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(2000, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                    shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]    shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]     shouldBe "BalanceReached2000"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String]   shouldBe "Well done"
      (response.json \ "milestones" \ 0 \ "milestoneMessage").as[String] shouldBe "You have £2,000 in savings now."
    }

    "respond with 200 and the BalanceReached2400 milestone in a list as JSON when the milestone is hit" in {
      val nino = generator.nextNino
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalance(0, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(2400, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                  shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]  shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]   shouldBe "BalanceReached2400"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String] shouldBe "You have £2,400 in savings"
      (response.json \ "milestones" \ 0 \ "milestoneMessage")
        .as[String] shouldBe "You have saved the most possible with Help to Save!"
    }

    "respond with 200 and an empty list when the same milestone has been hit twice and is not repeatable" in {
      val nino = generator.nextNino
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalance(0, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(1, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())
      await(wsUrl(s"/savings-account/$nino/milestones/BalanceReached/seen?journeyId=$journeyId").put(""))

      loginWithBalance(0, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(1, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                       shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").asOpt[String]    shouldBe None
      (response.json \ "milestones" \ 0 \ "milestoneKey").asOpt[String]     shouldBe None
      (response.json \ "milestones" \ 0 \ "milestoneTitle").asOpt[String]   shouldBe None
      (response.json \ "milestones" \ 0 \ "milestoneMessage").asOpt[String] shouldBe None
    }

    "respond with 200 and the same milestone after it has been hit once before if it is repeatable" in {
      val nino = generator.nextNino
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalance(0, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(250, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())
      await(wsUrl(s"/savings-account/$nino/milestones/BalanceReached/seen?journeyId=$journeyId").put(""))

      loginWithBalance(0, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(270, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                    shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]    shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]     shouldBe "BalanceReached200"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String]   shouldBe "Well done for saving £200 so far"
      (response.json \ "milestones" \ 0 \ "milestoneMessage").as[String] shouldBe "Your savings are growing."
    }

    "respond with 200 and only the most recent milestone of a specific type in a list as JSON" in {
      val nino = generator.nextNino
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalance(0, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(1, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(100, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

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

    "respond with 200 and the BalanceReached1 milestone in a list as JSON when the milestone is hit and the account is closed" in {
      val nino = generator.nextNino
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalanceAndBonusTerms(0, nino, 0, 0, LocalDate.now().plusDays(40), 4, LocalDate.now().plusYears(2), true)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalanceAndBonusTerms(1, nino, 0, 0, LocalDate.now().plusDays(40), 0, LocalDate.now().plusYears(2), true)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                  shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]  shouldBe "BalanceReached"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]   shouldBe "BalanceReached1"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String] shouldBe "You've started saving"
      (response.json \ "milestones" \ 0 \ "milestoneMessage")
        .as[String] shouldBe "Well done for making your first payment."
    }

    "respond with 200 and the EndOfFirstBonusPeriodPositiveBonus milestone in a list as JSON when the milestone is hit" in {
      val nino         = generator.nextNino
      val firstEndDate = LocalDate.now().plusDays(19)
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalanceAndBonusTerms(10, nino, 200, 0, firstEndDate, 400, LocalDate.now().plusYears(2))
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                  shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]  shouldBe "BonusPeriod"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]   shouldBe "EndOfFirstBonusPeriodPositiveBonus"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String] shouldBe "It's nearly the end of year 2"
      (response.json \ "milestones" \ 0 \ "milestoneMessage")
        .as[String] shouldBe s"Your first bonus of £200 will be paid into your bank account from ${firstEndDate.plusDays(1).format(dateFormat)}."
    }

    "respond with 200 and the StartOfFinalBonusPeriodNoBonus milestone in a list as JSON when the milestone is hit" in {
      val nino = generator.nextNino
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalanceAndBonusTerms(10, nino, 0, 0, LocalDate.now().minusDays(89), 0, LocalDate.now().plusYears(2))
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                 shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String] shouldBe "BonusPeriod"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]  shouldBe "StartOfFinalBonusPeriodNoBonus"
      (response.json \ "milestones" \ 0 \ "milestoneTitle")
        .as[String] shouldBe "Your Help to Save account is 2 years old"
      (response.json \ "milestones" \ 0 \ "milestoneMessage")
        .as[String] shouldBe "There are still 2 years to use your account to save and earn a tax-free bonus from the government."
    }

    "respond with 200 and the EndOfFirstBonusPeriodMaximumBonusPaid milestone in a list as JSON when the milestone is hit" in {
      val nino         = generator.nextNino
      val firstEndDate = LocalDate.now().minusDays(1)
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalanceAndBonusTerms(10, nino, 200, 600, firstEndDate, 400, LocalDate.now().plusYears(2))
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                  shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]  shouldBe "BonusPeriod"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]   shouldBe "EndOfFirstBonusPeriodMaximumBonusPaid"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String] shouldBe "Congratulations"
      (response.json \ "milestones" \ 0 \ "milestoneMessage")
        .as[String] shouldBe "You earned the maximum first bonus of £600."
    }

    "respond with 200 and the EndOfFirstBonusPeriodBonusPaid milestone in a list as JSON when the milestone is hit" in {
      val nino         = generator.nextNino
      val firstEndDate = LocalDate.now().minusDays(1)
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalanceAndBonusTerms(10, nino, 200, 350.75, firstEndDate, 400, LocalDate.now().plusYears(2))
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                  shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]  shouldBe "BonusPeriod"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]   shouldBe "EndOfFirstBonusPeriodBonusPaid"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String] shouldBe "Congratulations"
      (response.json \ "milestones" \ 0 \ "milestoneMessage")
        .as[String] shouldBe "You earned a £350.75 first bonus."
    }

    "respond with 200 and the EndOfFinalBonusPeriodZeroBalanceNoBonus milestone in a list as JSON when the milestone is hit" in {
      val nino          = generator.nextNino
      val secondEndDate = LocalDate.now().plusDays(19)
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalanceAndBonusTerms(0, nino, 0, 0, LocalDate.now().minusYears(2), 0, secondEndDate)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                  shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]  shouldBe "BonusPeriod"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]   shouldBe "EndOfFinalBonusPeriodZeroBalanceNoBonus"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String] shouldBe "It's nearly the end of year 4"
      (response.json \ "milestones" \ 0 \ "milestoneMessage")
        .as[String] shouldBe s"Your Help to Save account will be closed from ${secondEndDate.plusDays(1).format(dateFormat)}."
    }

    "respond with 200 and the EndOfFinalBonusPeriodZeroBalancePositiveBonus milestone in a list as JSON when the milestone is hit" in {
      val nino          = generator.nextNino
      val secondEndDate = LocalDate.now().plusDays(19)
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalanceAndBonusTerms(0, nino, 0, 0, LocalDate.now().minusYears(2), 250, secondEndDate)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                 shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String] shouldBe "BonusPeriod"
      (response.json \ "milestones" \ 0 \ "milestoneKey")
        .as[String]                                                    shouldBe "EndOfFinalBonusPeriodZeroBalancePositiveBonus"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String] shouldBe "It's nearly the end of year 4"
      (response.json \ "milestones" \ 0 \ "milestoneMessage")
        .as[String] shouldBe s"Your final bonus of £250 will be paid into your bank account from ${secondEndDate.plusDays(1).format(dateFormat)}."
    }

    "respond with 200 and the EndOfFinalBonusPeriodPositiveBalanceNoBonus milestone in a list as JSON when the milestone is hit" in {
      val nino          = generator.nextNino
      val secondEndDate = LocalDate.now().plusDays(19)
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalanceAndBonusTerms(1350, nino, 0, 0, LocalDate.now().minusYears(2), 0, secondEndDate)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                 shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String] shouldBe "BonusPeriod"
      (response.json \ "milestones" \ 0 \ "milestoneKey")
        .as[String]                                                    shouldBe "EndOfFinalBonusPeriodPositiveBalanceNoBonus"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String] shouldBe "It's nearly the end of year 4"
      (response.json \ "milestones" \ 0 \ "milestoneMessage")
        .as[String] shouldBe s"Your savings of £1350 will be paid into your bank account from ${secondEndDate.plusDays(1).format(dateFormat)}."
    }

    "respond with 200 and the EndOfFinalBonusPeriodPositiveBalancePositiveBonus milestone in a list as JSON when the milestone is hit" in {
      val nino          = generator.nextNino
      val secondEndDate = LocalDate.now().plusDays(19)
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalanceAndBonusTerms(1350, nino, 0, 0, LocalDate.now().minusYears(2), 600, secondEndDate)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                 shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String] shouldBe "BonusPeriod"
      (response.json \ "milestones" \ 0 \ "milestoneKey")
        .as[String]                                                    shouldBe "EndOfFinalBonusPeriodPositiveBalancePositiveBonus"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String] shouldBe "It's nearly the end of year 4"
      (response.json \ "milestones" \ 0 \ "milestoneMessage")
        .as[String] shouldBe s"Your savings of £1350 and final bonus of £600 will be paid into your bank account from ${secondEndDate.plusDays(1).format(dateFormat)}."
    }

    "respond with 200 and the EndOfFinalBonusPeriodMaximumBonusPaid milestone in a list as JSON when the milestone is hit" in {
      val nino          = generator.nextNino
      val secondEndDate = LocalDate.now().minusDays(1)
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalanceAndBonusTerms(1350,
                                    nino,
                                    0,
                                    0,
                                    LocalDate.now().minusYears(2),
                                    600,
                                    secondEndDate,
                                    secondPeriodBonusPaid = 600)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                 shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String] shouldBe "BonusPeriod"
      (response.json \ "milestones" \ 0 \ "milestoneKey")
        .as[String]                                                    shouldBe "EndOfFinalBonusPeriodMaximumBonusPaid"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String] shouldBe "Congratulations"
      (response.json \ "milestones" \ 0 \ "milestoneMessage")
        .as[String] shouldBe "You earned the maximum final bonus of £600."
    }

    "respond with 200 and the EndOfFinalBonusPeriodBonusPaid milestone in a list as JSON when the milestone is hit" in {
      val nino          = generator.nextNino
      val secondEndDate = LocalDate.now().minusDays(1)
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalanceAndBonusTerms(1350,
                                    nino,
                                    0,
                                    0,
                                    LocalDate.now().minusYears(2),
                                    350.75,
                                    secondEndDate,
                                    secondPeriodBonusPaid = 350.75)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                 shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String] shouldBe "BonusPeriod"
      (response.json \ "milestones" \ 0 \ "milestoneKey")
        .as[String]                                                    shouldBe "EndOfFinalBonusPeriodBonusPaid"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String] shouldBe "Congratulations"
      (response.json \ "milestones" \ 0 \ "milestoneMessage")
        .as[String] shouldBe "You earned a £350.75 final bonus."
    }

    "respond with 200 and an empty list when the same bonus period milestone has been hit twice" in {
      val nino = generator.nextNino
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalanceAndBonusTerms(10, nino, 200, 0, LocalDate.now().plusDays(19), 400, LocalDate.now().plusYears(2))
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())
      await(wsUrl(s"/savings-account/$nino/milestones/BonusPeriod/seen?journeyId=$journeyId").put(""))

      loginWithBalanceAndBonusTerms(10, nino, 200, 0, LocalDate.now().plusDays(19), 400, LocalDate.now().plusYears(2))
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                       shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").asOpt[String]    shouldBe None
      (response.json \ "milestones" \ 0 \ "milestoneKey").asOpt[String]     shouldBe None
      (response.json \ "milestones" \ 0 \ "milestoneTitle").asOpt[String]   shouldBe None
      (response.json \ "milestones" \ 0 \ "milestoneMessage").asOpt[String] shouldBe None

    }

    "respond with 200 and only the highest priority milestone in a list as JSON" in {
      val nino         = generator.nextNino
      val firstEndDate = LocalDate.now().plusDays(19)
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalance(0, nino)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(1, nino)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalanceAndBonusTerms(10, nino, 200, 0, firstEndDate, 400, LocalDate.now().plusYears(2))
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                  shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").as[String]  shouldBe "BonusPeriod"
      (response.json \ "milestones" \ 0 \ "milestoneKey").as[String]   shouldBe "EndOfFirstBonusPeriodPositiveBonus"
      (response.json \ "milestones" \ 0 \ "milestoneTitle").as[String] shouldBe "It's nearly the end of year 2"
      (response.json \ "milestones" \ 0 \ "milestoneMessage")
        .as[String] shouldBe s"Your first bonus of £200 will be paid into your bank account from ${firstEndDate.plusDays(1).format(dateFormat)}."

      (response.json \ "milestones" \ 1 \ "milestoneType").asOpt[String]    shouldBe None
      (response.json \ "milestones" \ 1 \ "milestoneKey").asOpt[String]     shouldBe None
      (response.json \ "milestones" \ 1 \ "milestoneTitle").asOpt[String]   shouldBe None
      (response.json \ "milestones" \ 1 \ "milestoneMessage").asOpt[String] shouldBe None
    }

    "respond with 200 and an empty list when the account is closed and a bonus period milestone has been reached" in {
      val nino = generator.nextNino
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalanceAndBonusTerms(10,
                                    nino,
                                    200,
                                    0,
                                    LocalDate.now().plusDays(19),
                                    400,
                                    LocalDate.now().plusYears(2),
                                    true)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status                                                       shouldBe 200
      (response.json \ "milestones" \ 0 \ "milestoneType").asOpt[String]    shouldBe None
      (response.json \ "milestones" \ 0 \ "milestoneKey").asOpt[String]     shouldBe None
      (response.json \ "milestones" \ 0 \ "milestoneTitle").asOpt[String]   shouldBe None
      (response.json \ "milestones" \ 0 \ "milestoneMessage").asOpt[String] shouldBe None

    }

    "return 400 when journeyId is not supplied" in {
      val nino = generator.nextNino
      AuthStub.userIsLoggedIn(nino)

      val response: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones").get())

      response.status shouldBe 400
    }
  }

  "PUT /savings-account/:nino/milestones/:milestoneType/seen" should {
    "mark milestones of a BalanceReached type as seen using the nino and milestone type" in {
      val nino = generator.nextNino
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalance(0, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(1, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse =
        await(wsUrl(s"/savings-account/$nino/milestones/BalanceReached/seen?journeyId=$journeyId").put(""))
      val milestones: WSResponse = await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      response.status shouldBe 204

      milestones.status                                                       shouldBe 200
      (milestones.json \ "milestones" \ 0 \ "milestoneType").asOpt[String]    shouldBe None
      (milestones.json \ "milestones" \ 0 \ "milestoneKey").asOpt[String]     shouldBe None
      (milestones.json \ "milestones" \ 0 \ "milestoneTitle").asOpt[String]   shouldBe None
      (milestones.json \ "milestones" \ 0 \ "milestoneMessage").asOpt[String] shouldBe None
    }

    "mark milestones of a BonusPeriod type as seen using the nino and milestone type" in {
      val nino = generator.nextNino
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalanceAndBonusTerms(10, nino, 200, 0, LocalDate.now().plusDays(19), 400, LocalDate.now().plusYears(2))
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      val response: WSResponse =
        await(wsUrl(s"/savings-account/$nino/milestones/BonusPeriod/seen?journeyId=$journeyId").put(""))
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
      ShutteringStub.stubForShutteringDisabled()

      loginWithBalance(0, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(1, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      await(wsUrl(s"/savings-account/$nino/milestones/BalanceReached/seen?journeyId=$journeyId").put(""))
      await(wsUrl(s"/savings-account/$nino/milestones?journeyId=$journeyId").get())

      loginWithBalance(0, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

      loginWithBalance(1, nino, 0)
      await(wsUrl(s"/savings-account/$nino?journeyId=$journeyId").get())

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

    "return 400 when invalid journeyId is supplied" in {
      val nino = generator.nextNino
      AuthStub.userIsLoggedIn(nino)

      val response: WSResponse = await(
        wsUrl(s"/savings-account/$nino/milestones/BalanceReached/seen?journeyId=ThisIsAnInvalidJourneyId").put("")
      )
      response.status shouldBe 400
    }

    "return 400 when invalid NINO supplied" in {
      AuthStub.userIsNotLoggedIn()
      val response: WSResponse = await(wsUrl(s"/savings-account/AA123123123/milestones?journeyId=$journeyId").get())
      response.status shouldBe 400
    }
  }

  private def loginWithBalance(
    balance:            BigDecimal,
    nino:               Nino,
    firstTermBonusPaid: BigDecimal = 90.99
  ) = {
    wireMockServer.resetAll()
    AuthStub.userIsLoggedIn(nino)
    HelpToSaveStub.currentUserIsEnrolled()
    HelpToSaveStub.accountExists(balance, nino, firstTermBonusPaid)
  }

  private def loginWithBalanceAndBonusTerms(
    balance:                   BigDecimal,
    nino:                      Nino,
    firstPeriodBonusEstimate:  BigDecimal,
    firstPeriodBonusPaid:      BigDecimal,
    firstPeriodEndDate:        LocalDate,
    secondPeriodBonusEstimate: BigDecimal,
    secondPeriodEndDate:       LocalDate,
    isClosed:                  Boolean = false,
    secondPeriodBonusPaid:     BigDecimal = 0
  ) = {
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
      secondPeriodBonusPaid,
      secondPeriodEndDate,
      isClosed
    )
  }
}
