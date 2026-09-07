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
import uk.gov.hmrc.mobilehelptosave.config.EncryptionConfig
import uk.gov.hmrc.mobilehelptosave.domain.{Eligibility, EligibilityRecord}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global

class MongoEligibilityRepoSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with DefaultPlayMongoRepositorySupport[EligibilityRecord] {

  private val collectionName = "poc-eligibility-hash-transition"
  private val enabledConfig  = TestEncryptionConfig(encryptionEnabled = true)
  private val disabledConfig = TestEncryptionConfig(encryptionEnabled = false)
  private val ninoHash       = new NinoHash(enabledConfig)

  override protected val repository: MongoEligibilityRepo =
    new MongoEligibilityRepo(mongoComponent, enabledConfig, ninoHash, collectionName)

  private lazy val legacyRepository =
    new MongoEligibilityRepo(mongoComponent, disabledConfig, ninoHash, collectionName)

  private val nino       = Nino("AA123456A")
  private val expireAt   = Instant.now.plus(28, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS)
  private val laterExpiry = expireAt.plus(7, ChronoUnit.DAYS)

  "setEligibility" should {
    "retain the existing NINO document shape when encryption is disabled" in {
      legacyRepository.setEligibility(Eligibility(nino, eligible = true, expireAt)).futureValue

      val stored = find(Filters.equal("nino", nino.nino)).futureValue.head
      stored.nino mustBe Some(nino)
      stored.hashNino mustBe None
      stored.expireAt mustBe expireAt
    }

    "write hashNino without nino when encryption is enabled" in {
      repository.setEligibility(Eligibility(nino, eligible = true, expireAt)).futureValue

      val stored = find(Filters.equal("hashNino", ninoHash(nino))).futureValue.head
      stored.nino mustBe None
      stored.hashNino mustBe Some(ninoHash(nino))
      stored.expireAt mustBe expireAt
    }

    "transition a legacy record and preserve its original expireAt" in {
      legacyRepository.setEligibility(Eligibility(nino, eligible = false, expireAt)).futureValue

      repository.setEligibility(Eligibility(nino, eligible = true, laterExpiry)).futureValue

      val stored = find(Filters.equal("hashNino", ninoHash(nino))).futureValue.head
      stored.nino mustBe None
      stored.eligible mustBe true
      stored.expireAt mustBe expireAt
    }

    "rewrite a hashed record and preserve its original expireAt" in {
      repository.setEligibility(Eligibility(nino, eligible = false, expireAt)).futureValue

      repository.setEligibility(Eligibility(nino, eligible = true, laterExpiry)).futureValue

      val stored = find(Filters.equal("hashNino", ninoHash(nino))).futureValue.head
      stored.nino mustBe None
      stored.eligible mustBe true
      stored.expireAt mustBe expireAt
    }
  }

  "getEligibility" should {
    "use only the NINO lookup when encryption is disabled" in {
      insert(EligibilityRecord(None, Some(ninoHash(nino)), eligible = true, expireAt)).futureValue

      legacyRepository.getEligibility(nino).futureValue mustBe None
    }

    "fall back to NINO when encryption is enabled and no hashNino record exists" in {
      insert(EligibilityRecord(Some(nino), None, eligible = true, expireAt)).futureValue

      repository.getEligibility(nino).futureValue mustBe Some(Eligibility(nino, eligible = true, expireAt))
    }

    "read by hashNino when encryption is enabled" in {
      insert(EligibilityRecord(None, Some(ninoHash(nino)), eligible = true, expireAt)).futureValue

      repository.getEligibility(nino).futureValue mustBe Some(Eligibility(nino, eligible = true, expireAt))
    }
  }

  "the unique sparse indexes" should {
    "allow hash-only and legacy NINO documents to coexist during transition" in {
      val secondNino = Nino("AA123457A")
      val legacyNino = Nino("AA123458A")

      legacyRepository.setEligibility(Eligibility(nino, eligible = true, expireAt)).futureValue
      legacyRepository.setEligibility(Eligibility(secondNino, eligible = false, expireAt)).futureValue
      legacyRepository.setEligibility(Eligibility(legacyNino, eligible = true, expireAt)).futureValue

      repository.setEligibility(Eligibility(nino, eligible = false, laterExpiry)).futureValue
      repository.setEligibility(Eligibility(secondNino, eligible = true, laterExpiry)).futureValue

      find(Filters.exists("hashNino", exists = true)).futureValue.size mustBe 2
      find(Filters.exists("nino", exists = true)).futureValue.size mustBe 1
      repository.getEligibility(nino).futureValue mustBe Some(Eligibility(nino, eligible = false, expireAt))
      repository.getEligibility(legacyNino).futureValue mustBe Some(Eligibility(legacyNino, eligible = true, expireAt))
    }
  }
}

private case class TestEncryptionConfig(
  encryptionEnabled: Boolean,
  encryptionHashKey: String = "c29tZS1sb25nLXRlc3QtaGFzaC1rZXk="
) extends EncryptionConfig
