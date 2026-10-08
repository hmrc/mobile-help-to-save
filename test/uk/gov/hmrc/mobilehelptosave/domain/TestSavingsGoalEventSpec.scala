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

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.domain.Nino

import java.time.{Instant, LocalDateTime, ZoneOffset}

class TestSavingsGoalEventSpec extends AnyWordSpec with Matchers {
  private val now = Instant.parse("2026-01-31T12:00:00Z")

  private def request(ttl: String) = TestSavingsGoalEvent(Nino("AA123456A"), "set", Some(100), None, ttl, isHashed = true)

  "expireAtFrom" should {
    "accept readable TTL units" in {
      request("30 minutes").expireAtFrom(now) mustBe Right(now.plusSeconds(1800))
      request("1 day").expireAtFrom(now) mustBe Right(now.plusSeconds(86400))
      request("6 months").expireAtFrom(now) mustBe
        Right(LocalDateTime.ofInstant(now, ZoneOffset.UTC).plusMonths(6).toInstant(ZoneOffset.UTC))
    }

    "accept compact TTL units" in {
      request("5m").expireAtFrom(now) mustBe Right(now.plusSeconds(300))
      request("2h").expireAtFrom(now) mustBe Right(now.plusSeconds(7200))
      request("1mo").expireAtFrom(now) mustBe
        Right(LocalDateTime.ofInstant(now, ZoneOffset.UTC).plusMonths(1).toInstant(ZoneOffset.UTC))
    }

    "reject invalid or non-positive TTL values" in {
      request("0 days").expireAtFrom(now) mustBe Left("ttl must be greater than zero")
      request("tomorrow").expireAtFrom(now).isLeft mustBe true
    }
  }
}
