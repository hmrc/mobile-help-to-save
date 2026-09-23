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

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.domain.Nino
import java.time.{Instant, LocalDateTime, ZoneOffset}

case class TestPreviousBalance(nino: Nino, previousBalance: BigDecimal, ttl: String, isHashed: Boolean) {
  def expireAtFrom(now: Instant): Either[String, Instant] =
    ttl.trim.toLowerCase match {
      case TestPreviousBalance.TtlPattern(amountText, unit) =>
        val amount = amountText.toLong
        if (amount <= 0) Left("ttl must be greater than zero")
        else
          unit match {
            case "s" | "second" | "seconds" => Right(now.plusSeconds(amount))
            case "m" | "minute" | "minutes" => Right(now.plusSeconds(amount * 60))
            case "h" | "hour" | "hours"     => Right(now.plusSeconds(amount * 60 * 60))
            case "d" | "day" | "days"       => Right(now.plusSeconds(amount * 24 * 60 * 60))
            case "mo" | "month" | "months" =>
              Right(LocalDateTime.ofInstant(now, ZoneOffset.UTC).plusMonths(amount).toInstant(ZoneOffset.UTC))
          }
      case _ => Left("ttl must use a supported format such as '30 minutes', '1 day', or '6 months'")
    }
}

object TestPreviousBalance {
  private val TtlPattern = """^(\d+)\s*(s|seconds?|m|minutes?|h|hours?|d|days?|mo|months?)$""".r

  implicit val format: OFormat[TestPreviousBalance] = Json.format[TestPreviousBalance]
}
