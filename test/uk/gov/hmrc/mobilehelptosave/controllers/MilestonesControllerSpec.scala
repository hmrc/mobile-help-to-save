/*
 * Copyright 2023 HM Revenue & Customs
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

import eu.timepit.refined.auto._
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.services.MilestonesService
import uk.gov.hmrc.mobilehelptosave.support.{BaseSpec, TestF}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MilestonesControllerSpec extends BaseSpec with TestF {

  private val mockMilestonesService = mock[MilestonesService[Future]]

  "getMilestones" should {
    "return 200 and the list of milestones as JSON" in {
      val milestones = List(
        MongoMilestone(nino          = nino,
                       milestoneType = BalanceReached,
                       milestone     = Milestone(BalanceReached1),
                       isRepeatable  = false)
      )

      (mockMilestonesService
        .getMilestones(_: Nino)(_: HeaderCarrier))
        .expects(nino, *)
        .returning(Future.successful(milestones))

      val controller =
        new MilestonesController(logger,
                                 mockMilestonesService,
                                 new AlwaysAuthorisedWithIds(nino),
                                 stubControllerComponents())

      val result = controller.getMilestones(nino, "02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())

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
        new MilestonesController(logger,
                                 mockMilestonesService,
                                 new AlwaysAuthorisedWithIds(nino),
                                 stubControllerComponents())

      val result = controller.markAsSeen(nino, "BalancedReached", "02940b73-19cc-4c31-80d3-f4deb851c707")(FakeRequest())

      status(result) shouldBe 204
    }
  }

}
