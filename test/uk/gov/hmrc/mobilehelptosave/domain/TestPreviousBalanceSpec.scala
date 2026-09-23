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

import java.time.Instant

class TestPreviousBalanceSpec extends AnyWordSpec with Matchers {
  private val now = Instant.parse("2026-01-31T12:00:00Z")

  private def request(ttl: String) = TestPreviousBalance(Nino("AA123456A"), BigDecimal(10), ttl, isHashed = true)

  "expireAtFrom" should {
    "accept compact and readable fixed-duration TTL values" in {
      request("30m").expireAtFrom(now) mustBe Right(Instant.parse("2026-01-31T12:30:00Z"))
      request("1 day").expireAtFrom(now) mustBe Right(Instant.parse("2026-02-01T12:00:00Z"))
    }

    "apply calendar-month semantics to a month TTL" in {
      request("1 month").expireAtFrom(now) mustBe Right(Instant.parse("2026-02-28T12:00:00Z"))
    }

    "reject unsupported or non-positive TTL values" in {
      request("0 days").expireAtFrom(now) mustBe Left("ttl must be greater than zero")
      request("tomorrow").expireAtFrom(now) mustBe
        Left("ttl must use a supported format such as '30 minutes', '1 day', or '6 months'")
    }
  }
}
