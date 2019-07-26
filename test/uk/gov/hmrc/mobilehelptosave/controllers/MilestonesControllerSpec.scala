/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.mobilehelptosave.controllers

import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OneInstancePerTest, WordSpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.MilestonesControllerConfig
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.services.MilestonesService
import uk.gov.hmrc.mobilehelptosave.support.{LoggerStub, TestF}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MilestonesControllerSpec
    extends WordSpec
    with Matchers
    with MockFactory
    with OneInstancePerTest
    with FutureAwaits
    with DefaultAwaitTimeout
    with LoggerStub
    with TestF {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val generator = new Generator(0)
  private val nino      = generator.nextNino

  private val mockMilestonesService = mock[MilestonesService[Future]]

  private val trueShuttering  = Shuttering(shuttered = true, "Shuttered", "HTS is currently not available")
  private val falseShuttering = Shuttering(shuttered = false, "", "")

  case class TestMilestonesControllerConfig(
    shuttering: Shuttering
  ) extends MilestonesControllerConfig

  private val config = TestMilestonesControllerConfig(
    falseShuttering
  )

  "getMilestones" should {
    "return 200 and the list of milestones as JSON" in {
      val milestones = List(Milestone(nino = nino, milestoneType = BalanceReached, milestoneKey = StartedSaving, isRepeatable = false))

      (mockMilestonesService
        .getMilestones(_: Nino)(_: HeaderCarrier))
        .expects(nino, *)
        .returning(Future.successful(milestones))

      val controller =
        new MilestonesController(logger, mockMilestonesService, new AlwaysAuthorisedWithIds(nino), config, stubControllerComponents())

      val result = controller.getMilestones(nino.value)(FakeRequest())

      status(result)        shouldBe 200
      contentAsJson(result) shouldBe Json.toJson(Milestones(milestones))
    }
  }

  "markAsSeen" should {
    "return 204 when the milestone has been marked as seen" in {
      (mockMilestonesService
        .markAsSeen(_: Nino, _: String)(_: HeaderCarrier))
        .expects(nino, *, *)
        .returning(Future.successful(()))

      val controller =
        new MilestonesController(logger, mockMilestonesService, new AlwaysAuthorisedWithIds(nino), config, stubControllerComponents())

      val result = controller.markAsSeen(nino.value, "5d3181afa400004cdf56dc76")(FakeRequest())

      status(result) shouldBe 204
    }
  }

}
