/*
 * Copyright 2018 HM Revenue & Customs
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

import org.joda.time.LocalDate
import play.api.libs.json.{Json, Writes}

case class BonusTerm(
  bonusEstimate: BigDecimal,
  bonusPaid: BigDecimal,
  endDate: LocalDate,
  bonusPaidOnOrAfterDate: LocalDate
)

object BonusTerm {
  implicit val writes: Writes[BonusTerm] = Json.writes[BonusTerm]
}

case class Blocking(
  unspecified: Boolean
)

object Blocking {
  implicit val writes: Writes[Blocking] = Json.writes[Blocking]
}

case class Account(
  isClosed: Boolean,

  blocked: Blocking,

  balance: BigDecimal,

  paidInThisMonth: BigDecimal,
  canPayInThisMonth: BigDecimal,
  maximumPaidInThisMonth: BigDecimal,
  thisMonthEndDate: LocalDate,

  bonusTerms: Seq[BonusTerm],

  closureDate: Option[LocalDate] = None,
  closingBalance: Option[BigDecimal] = None
)

object Account {
  implicit val writes: Writes[Account] = Json.writes[Account]
}
