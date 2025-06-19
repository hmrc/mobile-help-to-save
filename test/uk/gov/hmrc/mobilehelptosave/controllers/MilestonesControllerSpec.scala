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

package uk.gov.hmrc.mobilehelptosave.controllers

import eu.timepit.refined.auto.*
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.domain.*
import uk.gov.hmrc.mobilehelptosave.services.MilestonesService
import uk.gov.hmrc.mobilehelptosave.support.BaseSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import play.api.LoggerLike
import uk.gov.hmrc.mobilehelptosave.connectors.HttpClientV2Helper
import uk.gov.hmrc.mobilehelptosave.domain.types.JourneyId

import scala.util.{Failure, Success}
class MilestonesControllerSpec extends HttpClientV2Helper {

  private val logger = mock[LoggerLike]
  private val mockMilestonesService = mock[MilestonesService]
  val jid: JourneyId = JourneyId.from("02940b73-19cc-4c31-80d3-f4deb851c707").toOption.get



  "getMilestones" should {
    "return 200 and the list of milestones as JSON" in {
      val milestones = List(
        MongoMilestone(nino          = nino,
                       milestoneType = BalanceReached,
                       milestone     = Milestone(BalanceReached1),
                       isRepeatable  = false)
      )
      when(mockMilestonesService.getMilestones(any[Nino])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(milestones))
      mockMilestonesService.getMilestones(any[Nino])(any[HeaderCarrier], any[ExecutionContext]) onComplete {
        case Success(_) => Right(Some(milestones))
        case Failure(_) =>
      }

      val controller =
        new MilestonesController(logger,
                                 mockMilestonesService,
                                 new AlwaysAuthorisedWithIds(nino),
                                 stubControllerComponents())

      val result = controller.getMilestones(nino, jid)(FakeRequest())

      status(result)        mustBe  200
      contentAsJson(result) mustBe  Json.toJson(Milestones(milestones))
    }
  }
  "markAsSeen" should {
    "return 204 when the milestone has been marked as seen" in {
      when(mockMilestonesService.markAsSeen(any[Nino],any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      mockMilestonesService.markAsSeen(any[Nino],any[String])(any[HeaderCarrier]) onComplete {
        case Success(_) => Right(Some(()))
        case Failure(_) =>
      }
    }
  }


}
