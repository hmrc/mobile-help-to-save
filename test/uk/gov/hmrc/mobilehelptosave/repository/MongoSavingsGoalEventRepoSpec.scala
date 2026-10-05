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
import uk.gov.hmrc.mobilehelptosave.config.MongoConfig
import uk.gov.hmrc.mobilehelptosave.domain.SavingsGoal
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.{Instant, LocalDate, ZoneOffset}
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global

class MongoSavingsGoalEventRepoSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with DefaultPlayMongoRepositorySupport[SavingsGoalEventRecord] {

  private val enabledConfig = SavingsGoalTestMongoConfig(encryptionEnabled = true)
  private val disabledConfig = enabledConfig.copy(encryptionEnabled = false)
  private val ninoHash = new NinoHash(enabledConfig)

  override protected val repository: MongoSavingsGoalEventRepo =
    new MongoSavingsGoalEventRepo(mongoComponent, enabledConfig, ninoHash)

  private lazy val legacyRepository =
    new MongoSavingsGoalEventRepo(mongoComponent, disabledConfig, ninoHash)

  private val nino = Nino("AA123456A")
  private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  private val originalExpiry = now.plus(180, ChronoUnit.DAYS)

  "setGoal and deleteGoal" should {
    "retain the legacy NINO shape when encryption is disabled" in {
      val bonusDate = LocalDate.of(2026, 1, 31)

      legacyRepository.setGoal(nino, Some(100), Some("Holiday"), bonusDate).futureValue

      val stored = find(Filters.equal("nino", nino.nino)).futureValue.head
      stored.nino mustBe Some(nino)
      stored.hashNino mustBe None
      stored.expireAt mustBe bonusDate.plusMonths(6).atStartOfDay().toInstant(ZoneOffset.UTC)
    }

    "write set and delete events with hashNino only when encryption is enabled" in {
      val bonusDate = LocalDate.of(2026, 2, 1)

      repository.setGoal(nino, Some(100), Some("Holiday"), bonusDate).futureValue
      repository.deleteGoal(nino, bonusDate).futureValue

      val records = find(Filters.equal("hashNino", ninoHash(nino))).futureValue
      records must have size 2
      all(records.map(_.nino)) mustBe None
      records.map(_.getClass).toSet mustBe Set(classOf[SavingsGoalSetEventRecord], classOf[SavingsGoalDeleteEventRecord])
    }

    "use the configured TTL in calendar months" in {
      val twoMonthConfig = enabledConfig.copy(savingsGoalEventsTtlMonths = 2)
      val twoMonthRepository = new MongoSavingsGoalEventRepo(mongoComponent, twoMonthConfig, ninoHash)
      val bonusDate = LocalDate.of(2026, 1, 31)

      twoMonthRepository.setGoal(nino, Some(100), None, bonusDate).futureValue

      find(Filters.equal("hashNino", ninoHash(nino))).futureValue.head.expireAt mustBe
        bonusDate.plusMonths(2).atStartOfDay().toInstant(ZoneOffset.UTC)
    }
  }

  "getEvents" should {
    "read records using hashNino" in {
      val record = SavingsGoalSetEventRecord(None, Some(ninoHash(nino)), Some(100), now, Some("Holiday"), originalExpiry)
      insert(record).futureValue

      repository.getEvents(nino).futureValue mustBe Seq(record.toDomain(nino))
    }

    "fall back to NINO, migrate every legacy event, and preserve expiry" in {
      val secondDate = now.plusSeconds(1)
      insert(SavingsGoalSetEventRecord(Some(nino), None, Some(100), now, None, originalExpiry)).futureValue
      insert(SavingsGoalDeleteEventRecord(Some(nino), None, secondDate, originalExpiry)).futureValue

      repository.getEvents(nino).futureValue.map(_.date) mustBe Seq(now, secondDate)

      val migrated = find(Filters.equal("hashNino", ninoHash(nino))).futureValue
      migrated must have size 2
      all(migrated.map(_.nino)) mustBe None
      all(migrated.map(_.expireAt)) mustBe originalExpiry
    }

    "return a complete mixed set while migrating only the legacy records" in {
      val legacyDate = now.plusSeconds(1)
      insert(SavingsGoalSetEventRecord(None, Some(ninoHash(nino)), Some(50), now, None, originalExpiry)).futureValue
      insert(SavingsGoalSetEventRecord(Some(nino), None, Some(100), legacyDate, None, originalExpiry)).futureValue

      repository.getEvents(nino).futureValue.map(_.date) mustBe Seq(now, legacyDate)
      find(Filters.equal("nino", nino.nino)).futureValue mustBe empty
      find(Filters.equal("hashNino", ninoHash(nino))).futureValue must have size 2
    }

    "use only NINO when encryption is disabled" in {
      insert(SavingsGoalSetEventRecord(None, Some(ninoHash(nino)), Some(100), now, None, originalExpiry)).futureValue

      legacyRepository.getEvents(nino).futureValue mustBe empty
    }
  }

  "getGoal" should {
    "derive the current goal from the latest event across hashed and legacy records" in {
      insert(SavingsGoalSetEventRecord(None, Some(ninoHash(nino)), Some(50), now, None, originalExpiry)).futureValue
      insert(SavingsGoalSetEventRecord(Some(nino), None, Some(100), now.plusSeconds(1), Some("Car"), originalExpiry)).futureValue

      repository.getGoal(nino).futureValue mustBe Some(SavingsGoal(Some(100), Some("Car")))
    }

    "return no goal when the latest event deletes it" in {
      insert(SavingsGoalSetEventRecord(None, Some(ninoHash(nino)), Some(50), now, None, originalExpiry)).futureValue
      insert(SavingsGoalDeleteEventRecord(Some(nino), None, now.plusSeconds(1), originalExpiry)).futureValue

      repository.getGoal(nino).futureValue mustBe None
    }
  }

}

private case class SavingsGoalTestMongoConfig(
  encryptionEnabled: Boolean,
  encryptionHashKey: String = "c29tZS1sb25nLXRlc3QtaGFzaC1rZXk=",
  mongoUri: String = "",
  override val savingsGoalEventsTtlMonths: Long = 6
) extends MongoConfig
