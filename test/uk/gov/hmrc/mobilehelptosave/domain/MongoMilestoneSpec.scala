/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.mobilehelptosave.domain

import java.time.Instant
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*
import uk.gov.hmrc.domain.Nino

class MongoMilestoneSpec extends AnyWordSpec with Matchers {

  private val nino = Nino("AA123456A")
  private val generatedDate = Instant.parse("2025-01-01T10:00:00Z")
  private val expireAt = Instant.parse("2029-07-01T10:00:00Z")

  private val milestone =
    Milestone(BalanceReached100)

  private def buildMilestone(
    milestoneType: MilestoneType = BonusPeriod,
    isSeen: Boolean = false,
    isRepeatable: Boolean = true,
    updateRequired: Boolean = false
  ): MongoMilestone =
    MongoMilestone(
      nino           = nino,
      milestoneType  = milestoneType,
      milestone      = milestone,
      isSeen         = isSeen,
      isRepeatable   = isRepeatable,
      generatedDate  = generatedDate,
      expireAt       = expireAt,
      updateRequired = updateRequired
    )

  "MongoMilestone" should {

    "use the expected default values" in {
      val result = MongoMilestone(
        nino          = nino,
        milestoneType = BonusPeriod,
        milestone     = milestone
      )

      result.isSeen mustBe false
      result.isRepeatable mustBe true
      result.updateRequired mustBe false
      result.generatedDate must not be null
      result.expireAt      must not be null
    }

    "compare milestone types by priority" in {
      buildMilestone(BonusPeriod).compare(BonusReached) must be < 0
      buildMilestone(BonusReached).compare(BonusPeriod) must be > 0
      buildMilestone(BalanceReached).compare(BalanceReached) mustBe 0
    }

    "order milestones by ascending priority" in {
      val milestones = List(
        buildMilestone(BalanceReached),
        buildMilestone(BonusReached),
        buildMilestone(BonusPeriod)
      )

      milestones.sorted.map(_.milestoneType) mustBe List(
        BonusPeriod,
        BonusReached,
        BalanceReached
      )
    }

    "convert to a MongoMilestoneRecord preserving all fields" in {
      val source = buildMilestone(
        milestoneType  = BonusReached,
        isSeen         = true,
        isRepeatable   = false,
        updateRequired = true
      )

      val result = source.toMongoMilestoneRecord(Some("hashed-nino"))

      result.nino mustBe Some(nino)
      result.hashNino mustBe Some("hashed-nino")
      result.milestoneType mustBe source.milestoneType
      result.milestone mustBe source.milestone
      result.isSeen mustBe source.isSeen
      result.isRepeatable mustBe source.isRepeatable
      result.generatedDate mustBe generatedDate
      result.expireAt mustBe expireAt
      result.updateRequired mustBe true
    }

    "preserve an absent hash NINO during conversion" in {
      val result = buildMilestone().toMongoMilestoneRecord(None)

      result.hashNino mustBe None
      result.nino mustBe Some(nino)
    }

    "round-trip through JSON" in {
      val source = buildMilestone(
        milestoneType  = BonusReached,
        isSeen         = true,
        isRepeatable   = false,
        updateRequired = true
      )

      val json = Json.toJson(source)
      val result = json.as[MongoMilestone]

      result mustBe source
    }
  }
}
