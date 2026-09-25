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
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global

class MongoPreviousBalanceRepoSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with DefaultPlayMongoRepositorySupport[PreviousBalanceRecord] {

  private val collectionName = "previous-balance-repo-spec"
  private val enabledConfig = PreviousBalanceTestMongoConfig(encryptionEnabled = true)
  private val disabledConfig = PreviousBalanceTestMongoConfig(encryptionEnabled = false)
  private val ninoHash = new NinoHash(enabledConfig)

  override protected val repository: MongoPreviousBalanceRepo =
    new MongoPreviousBalanceRepo(mongoComponent, enabledConfig, ninoHash, collectionName)

  private lazy val legacyRepository =
    new MongoPreviousBalanceRepo(mongoComponent, disabledConfig, ninoHash, collectionName)

  private val nino = Nino("AA123456A")
  private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  private val originalExpiry = now.plus(180, ChronoUnit.DAYS)
  private val laterExpiry = originalExpiry.plus(30, ChronoUnit.DAYS)
  private val finalBonusPaidByDate = LocalDateTime.ofInstant(originalExpiry, ZoneOffset.UTC).minusMonths(6)

  "setPreviousBalance" should {
    "retain the legacy NINO shape when encryption is disabled" in {
      legacyRepository.setPreviousBalance(nino, BigDecimal(10), finalBonusPaidByDate).futureValue

      val stored = find(Filters.equal("nino", nino.nino)).futureValue.head
      stored.nino mustBe Some(nino)
      stored.hashNino mustBe None
      stored.previousBalance mustBe BigDecimal(10)
      stored.expireAt mustBe finalBonusPaidByDate.plusMonths(6).toInstant(ZoneOffset.UTC)
    }

    "write hashNino without NINO when encryption is enabled" in {
      repository.setPreviousBalance(nino, BigDecimal(20), finalBonusPaidByDate).futureValue

      val stored = find(Filters.equal("hashNino", ninoHash(nino))).futureValue.head
      stored.nino mustBe None
      stored.hashNino mustBe Some(ninoHash(nino))
      stored.previousBalance mustBe BigDecimal(20)
    }

    "transition a legacy record without changing its expiry" in {
      insert(PreviousBalanceRecord(Some(nino), None, BigDecimal(10), now, originalExpiry)).futureValue

      repository.setPreviousBalance(nino, BigDecimal(20), LocalDateTime.ofInstant(laterExpiry, ZoneOffset.UTC)).futureValue

      val stored = find(Filters.equal("hashNino", ninoHash(nino))).futureValue.head
      stored.nino mustBe None
      stored.previousBalance mustBe BigDecimal(20)
      stored.expireAt mustBe originalExpiry
    }

    "rewrite a hashed record without changing its expiry" in {
      insert(PreviousBalanceRecord(None, Some(ninoHash(nino)), BigDecimal(10), now, originalExpiry)).futureValue

      repository.setPreviousBalance(nino, BigDecimal(20), LocalDateTime.ofInstant(laterExpiry, ZoneOffset.UTC)).futureValue

      val stored = find(Filters.equal("hashNino", ninoHash(nino))).futureValue.head
      stored.previousBalance mustBe BigDecimal(20)
      stored.expireAt mustBe originalExpiry
    }
  }

  "getPreviousBalance" should {
    "read a hashNino record" in {
      insert(PreviousBalanceRecord(None, Some(ninoHash(nino)), BigDecimal(10), now, originalExpiry)).futureValue

      repository.getPreviousBalance(nino).futureValue mustBe
        Some(PreviousBalance(nino, BigDecimal(10), now, originalExpiry))
    }

    "fall back to NINO and migrate the record without changing its expiry" in {
      insert(PreviousBalanceRecord(Some(nino), None, BigDecimal(10), now, originalExpiry)).futureValue

      repository.getPreviousBalance(nino).futureValue mustBe
        Some(PreviousBalance(nino, BigDecimal(10), now, originalExpiry))

      val migrated = find(Filters.equal("hashNino", ninoHash(nino))).futureValue.head
      migrated.nino mustBe None
      migrated.expireAt mustBe originalExpiry
    }

    "use only NINO when encryption is disabled" in {
      insert(PreviousBalanceRecord(None, Some(ninoHash(nino)), BigDecimal(10), now, originalExpiry)).futureValue

      legacyRepository.getPreviousBalance(nino).futureValue mustBe None
    }
  }

  "NINO-based expiry maintenance" should {
    "find and migrate a legacy update-required record" in {
      insert(PreviousBalanceRecord(Some(nino), None, BigDecimal(10), now, originalExpiry, updateRequired = true)).futureValue

      repository.getPreviousBalanceUpdateRequired(nino).futureValue mustBe
        Some(PreviousBalance(nino, BigDecimal(10), now, originalExpiry, updateRequired = true))

      find(Filters.equal("hashNino", ninoHash(nino))).futureValue.head.nino mustBe None
    }

    "update a hashed record and clear updateRequired" in {
      insert(PreviousBalanceRecord(None, Some(ninoHash(nino)), BigDecimal(10), now, originalExpiry, updateRequired = true)).futureValue
      val replacementExpiry = LocalDateTime.ofInstant(laterExpiry, ZoneOffset.UTC)

      repository.updateExpireAt(nino, replacementExpiry).futureValue

      val stored = find(Filters.equal("hashNino", ninoHash(nino))).futureValue.head
      stored.updateRequired mustBe false
      stored.expireAt mustBe laterExpiry
      stored.nino mustBe None
    }
  }

  "test-only previous balance operations" should {
    "store and retrieve a mixture of hashed and legacy records" in {
      val secondNino = Nino("AA123457A")
      val hashed = PreviousBalance(nino, BigDecimal(10), now, originalExpiry)
      val legacy = PreviousBalance(secondNino, BigDecimal(20), now.plusSeconds(1), laterExpiry)

      repository.setTestPreviousBalance(hashed, isHashed = true).futureValue
      repository.setTestPreviousBalance(legacy, isHashed = false).futureValue

      repository.getTestPreviousBalanceRecord(nino).futureValue.get.hashNino mustBe Some(ninoHash(nino))
      repository.getTestPreviousBalanceRecord(secondNino).futureValue.get.nino mustBe Some(secondNino)
      repository.getAllTestPreviousBalanceRecords().futureValue.map(_.nino) mustBe Seq(Some(secondNino), None)

      repository.deleteTestPreviousBalance(nino).futureValue mustBe true
      repository.deleteTestPreviousBalance(secondNino).futureValue mustBe true
    }
  }
}

private case class PreviousBalanceTestMongoConfig(
  encryptionEnabled: Boolean,
  encryptionHashKey: String = "c29tZS1sb25nLXRlc3QtaGFzaC1rZXk=",
  mongoUri: String = ""
) extends MongoConfig
