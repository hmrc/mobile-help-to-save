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

package uk.gov.hmrc.mobilehelptosave.repository

import org.mongodb.scala.model.Filters
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.domain.*
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import java.time.{Instant, LocalDateTime, ZoneId, ZoneOffset}
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global

class MilestoneRepoSpec extends AnyWordSpec with Matchers with ScalaFutures with DefaultPlayMongoRepositorySupport[MongoMilestoneRecord] {
  private val enabledConfig = TestMongoConfig(encryptionEnabled = true)
  private val disabledConfig = TestMongoConfig(encryptionEnabled = false)
  private val ninoHash = new NinoHash(enabledConfig)

  private val repositoryWithoutEncrypt: MongoMilestonesRepo = new MongoMilestonesRepo(
    mongoComponent,
    ninoHash,
    disabledConfig
  )
  override protected val repository: MongoMilestonesRepo = new MongoMilestonesRepo(
    mongoComponent,
    ninoHash,
    enabledConfig
  )

  private val nino1 = Nino("AA123456A")
  private val nino2 = Nino("AB123456A")
  private val expireAt = LocalDateTime.now(ZoneOffset.UTC).plusHours(4).toInstant(ZoneOffset.UTC)
  val mileStone = MongoMilestone(
    nino          = nino1,
    milestoneType = BonusPeriod,
    milestone     = Milestone(key = BalanceReached1),
    expireAt      = expireAt
  )

  "setMilestone" when {

    "encryption flag is disabled" should {

      "insert a new record if it doesn't exists already" in {

        repositoryWithoutEncrypt.collection.drop()
        repositoryWithoutEncrypt.setMilestone(mileStone).futureValue
        val result = find(Filters.equal("nino", nino1.nino)).futureValue
        result.size mustBe 1
        result.map(_.nino) mustBe Seq(Some(nino1))

      }

      "insert the same nino if it's isRepeatable is true" in {

        repositoryWithoutEncrypt.collection.drop()
        repositoryWithoutEncrypt.setMilestone(mileStone).futureValue
        repositoryWithoutEncrypt.setMilestone(mileStone.copy(milestoneType = BonusReached)).futureValue
        val result = find(Filters.equal("nino", nino1.nino)).futureValue
        result.size mustBe 2
        result.map(_.milestoneType) mustBe Seq(BonusPeriod, BonusReached)

      }

      "not insert the same nino if it's isRepeatable is false" in {

        repositoryWithoutEncrypt.collection.drop()
        repositoryWithoutEncrypt.setMilestone(mileStone.copy(isRepeatable = false)).futureValue
        repositoryWithoutEncrypt.setMilestone(mileStone.copy(milestoneType = BonusReached)).futureValue
        val result = find(Filters.equal("nino", nino1.nino)).futureValue
        result.size mustBe 1
        result.map(_.milestoneType) mustBe Seq(BonusPeriod)

      }

    }

    "encryption flag is enabled" should {

      "insert a new record if it doesn't exists already" in {

        repository.collection.drop()
        repository.setMilestone(mileStone).futureValue
        val result = find(Filters.equal("hashNino", ninoHash(nino1))).futureValue
        result.size mustBe 1
        result.map(_.hashNino) mustBe Seq(Some(ninoHash(nino1)))
        result.map(_.nino) mustBe Seq(None)

      }

      "insert the same nino if  isRepeatable is true" in {
        repository.collection.drop()
        repository.setMilestone(mileStone).futureValue
        repository.setMilestone(mileStone.copy(milestoneType = BonusReached)).futureValue
        val result: Seq[MongoMilestoneRecord] = find(Filters.equal("hashNino", ninoHash(nino1))).futureValue
        result.size mustBe 2
        result.map(_.milestoneType) mustBe Seq(BonusPeriod, BonusReached)

      }

      "not insert the same nino if it's isRepeatable is false" in {
        repository.collection.drop()
        repository.setMilestone(mileStone.copy(isRepeatable = false)).futureValue
        repository.setMilestone(mileStone.copy(milestoneType = BonusReached)).futureValue
        val result: Seq[MongoMilestoneRecord] = find(Filters.equal("hashNino", ninoHash(nino1))).futureValue
        result.size mustBe 1
        result.map(_.milestoneType) mustBe Seq(BonusPeriod)
      }

      "insert same nino if isRepeatable is true and update the old records with hashNino if nino is present" in {
        repositoryWithoutEncrypt.collection.drop()
        repositoryWithoutEncrypt.setMilestone(mileStone).futureValue
        val result = find(Filters.equal("nino", nino1.nino)).futureValue
        result.size mustBe 1
        result.map(_.nino) mustBe Seq(Some(nino1))
        repository.setMilestone(mileStone.copy(milestoneType = BonusReached)).futureValue
        val resultNew: Seq[MongoMilestoneRecord] = find(Filters.equal("hashNino", ninoHash(nino1))).futureValue
        resultNew.size mustBe 2
        resultNew.map(_.nino) mustBe Seq(None, None)
        resultNew.map(_.hashNino) mustBe Seq(Some(ninoHash(nino1)), Some(ninoHash(nino1)))

      }
    }

  }

  "getMileStoneRecord" when {

    "encryption flag is disabled" should {

      "return the milestone record with nino if it exists" in {
        repositoryWithoutEncrypt.collection.drop()
        repositoryWithoutEncrypt.setMilestone(mileStone).futureValue
        val result = repositoryWithoutEncrypt.getMilestones(nino1).futureValue
        result.size mustBe 1
        result.head.nino mustBe nino1

      }

      "return empty list if no records are found" in {
        repositoryWithoutEncrypt.collection.drop()
        val result = repositoryWithoutEncrypt.getMilestones(nino1).futureValue
        result.size mustBe 0

      }

    }

    "encryption flag is enabled" should {

      "return the milestone details  if  hashNino record is present and return nino in response" in {
        repository.collection.drop()
        repository.setMilestone(mileStone).futureValue
        val result: Seq[MongoMilestone] = repository.getMilestones(nino1).futureValue
        result.size mustBe 1
        result.head.nino mustBe nino1

      }

      "return the milestone details  if one nino and one hashNino record is present and update the nino one to ninohash" in {
        repository.collection.drop()
        repository.setMilestone(mileStone).futureValue
        repositoryWithoutEncrypt.setMilestone(mileStone.copy(milestoneType = BonusReached)).futureValue
        val result: Seq[MongoMilestone] = repository.getMilestones(nino1).futureValue
        val resultNew: Seq[MongoMilestoneRecord] = find(Filters.equal("hashNino", ninoHash(nino1))).futureValue
        result.size mustBe 2
        resultNew.map(_.nino) mustBe Seq(None, None)
        resultNew.map(_.hashNino) mustBe Seq(Some(ninoHash(nino1)), Some(ninoHash(nino1)))

      }
    }
  }

  "markAsSeen" when {

    "encryption flag is disabled" should {

      "update isSeen to true and set expireAt to after 6 months for a given nino, milestoneType and isSeen = false" in {
        repositoryWithoutEncrypt.collection.drop()
        repositoryWithoutEncrypt.setMilestone(mileStone).futureValue
        val result = find(Filters.equal("nino", nino1.nino)).futureValue
        result.head.isSeen mustBe false
        result.head.expireAt.truncatedTo(ChronoUnit.SECONDS) mustBe expireAt.truncatedTo(ChronoUnit.SECONDS)
        // call markSSeen
        repositoryWithoutEncrypt.markAsSeen(nino1, milestoneType = BonusPeriod.toString).futureValue
        val resultNew = find(Filters.equal("nino", nino1.nino)).futureValue
        resultNew.head.isSeen mustBe true
        val expireAt6Months = expireAt
          .atZone(ZoneOffset.UTC)
          .plusMonths(6)
          .toLocalDate

        resultNew.head.expireAt.atZone(ZoneOffset.UTC).toLocalDate mustBe expireAt6Months
      }

      " no updates if no record found for a  given nino, milestoneType and isSeen = false" in {
        repositoryWithoutEncrypt.collection.drop()
        repositoryWithoutEncrypt.setMilestone(mileStone.copy(isSeen = true)).futureValue
        val result = find(Filters.equal("nino", nino1.nino)).futureValue
        result.head.isSeen mustBe true
        result.head.expireAt.truncatedTo(ChronoUnit.SECONDS) mustBe expireAt.truncatedTo(ChronoUnit.SECONDS)
        // call markSSeen
        repositoryWithoutEncrypt.markAsSeen(nino1, milestoneType = BonusPeriod.toString).futureValue
        val resultNew = find(Filters.equal("nino", nino1.nino)).futureValue
        println("truncated" + expireAt.truncatedTo(ChronoUnit.SECONDS))
        result.head.expireAt.truncatedTo(ChronoUnit.SECONDS) mustBe expireAt.truncatedTo(ChronoUnit.SECONDS)
      }

    }

    "encryption flag is enabled" should {

      "update isSeen to true and set expireAt to after 6 months for a given nino, milestoneType and isSeen = false if hashNino value is present" in {
        repository.collection.drop()
        repository.setMilestone(mileStone).futureValue
        val result = find(Filters.equal("hashNino", ninoHash(nino1))).futureValue
        result.head.isSeen mustBe false
        result.head.expireAt.truncatedTo(ChronoUnit.SECONDS) mustBe expireAt.truncatedTo(ChronoUnit.SECONDS)

        // call markSSeen
        repository.markAsSeen(nino1, milestoneType = BonusPeriod.toString).futureValue
        val resultNew = find(Filters.equal("hashNino", ninoHash(nino1))).futureValue
        resultNew.head.isSeen mustBe true
        val expireAt6Months = expireAt
          .atZone(ZoneOffset.UTC)
          .plusMonths(6)
          .toLocalDate

        resultNew.head.expireAt.atZone(ZoneOffset.UTC).toLocalDate mustBe expireAt6Months
      }

      "update isSeen to true , set expireAt to after 6 months  and set hashNino for a given nino, milestoneType and isSeen = false if old nino text is present" in {
        repository.collection.drop()
        repositoryWithoutEncrypt.setMilestone(mileStone).futureValue
        val result = find(Filters.equal("nino", nino1.nino)).futureValue
        result.head.nino mustBe Some(nino1)
        result.head.isSeen mustBe false
        result.head.expireAt.truncatedTo(ChronoUnit.SECONDS) mustBe expireAt.truncatedTo(ChronoUnit.SECONDS)

        // call markSSeen
        repository.markAsSeen(nino1, milestoneType = BonusPeriod.toString).futureValue
        val resultNew = find(Filters.equal("hashNino", ninoHash(nino1))).futureValue
        resultNew.head.nino mustBe None
        resultNew.head.hashNino mustBe Some(ninoHash(nino1))
        resultNew.head.isSeen mustBe true
        val expireAt6Months = expireAt
          .atZone(ZoneOffset.UTC)
          .plusMonths(6)
          .toLocalDate

        resultNew.head.expireAt.atZone(ZoneOffset.UTC).toLocalDate mustBe expireAt6Months
      }

    }

  }

  "updateExpireAt" when {

    "encryption flag is disabled" should {

      "set expireAt with the given date time and updateRequired= false based on nino and  updateRequired= true" in {

        val newExpireAt = LocalDateTime.ofInstant(expireAt.plus(1, ChronoUnit.DAYS), ZoneId.of("Europe/London"))
        repositoryWithoutEncrypt.collection.drop()
        repositoryWithoutEncrypt.setMilestone(mileStone.copy(updateRequired = true)).futureValue
        val result = find(Filters.equal("nino", nino1.nino)).futureValue
        result.size mustBe 1
        result.head.nino mustBe Some(nino1)
        result.head.updateRequired mustBe true
        repositoryWithoutEncrypt.updateExpireAt(nino1, newExpireAt).futureValue
        val resultNew = find(Filters.equal("nino", nino1.nino)).futureValue
        resultNew.size mustBe 1
        resultNew.head.nino mustBe (Some(nino1))
        resultNew.head.updateRequired mustBe false
        resultNew.head.expireAt.atZone(ZoneOffset.UTC).toLocalDate mustBe newExpireAt.atZone(ZoneOffset.UTC).toLocalDate

      }

      "not set any values if there is no matching record for the given nino updateRequired= true" in {
        val newExpireAt = LocalDateTime.ofInstant(expireAt.plus(1, ChronoUnit.DAYS), ZoneId.of("Europe/London"))
        repositoryWithoutEncrypt.collection.drop()
        repositoryWithoutEncrypt.setMilestone(mileStone).futureValue
        val result = find(Filters.equal("nino", nino1.nino)).futureValue
        result.size mustBe 1
        result.head.nino mustBe Some(nino1)
        result.head.updateRequired mustBe false
        repositoryWithoutEncrypt.updateExpireAt(nino1, newExpireAt).futureValue
        val resultNew = find(Filters.equal("nino", nino1.nino)).futureValue
        result.head.updateRequired mustBe false
        resultNew.head.expireAt.atZone(ZoneOffset.UTC).toLocalDate mustBe expireAt.atZone(ZoneOffset.UTC).toLocalDate

      }
    }

  }

}
