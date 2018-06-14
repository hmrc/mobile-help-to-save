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

import java.time.LocalDate

import cats.Eq
import play.api.libs.json._

sealed trait Operation {
  def stringValue =  this match {
    case Credit => "credit"
    case Debit  => "debit"
  }
}
case object Credit extends Operation
case object Debit extends Operation

object Operation {
  implicit val eqOperation: Eq[Operation] = Eq.fromUniversalEquals
  implicit val writes: Writes[Operation] = new Writes[Operation] {
    override def writes(o: Operation): JsValue = JsString(o.stringValue)
  }

  implicit val reads: Reads[Operation] = new Reads[Operation] {
    override def reads(json: JsValue): JsResult[Operation] = json match {
      case JsString("credit") => JsSuccess(Credit)
      case JsString("debit") => JsSuccess(Debit)
      case JsString(unknown) => JsError(s"[$unknown] in not a valid Operation e.g. [Cradit|Debit]")
      case unknown => JsError(s"Cannot parse $unknown to a valid Operation e.g. [Cradit|Debit]")
    }
  }
}

case class Transaction(
  operation:            Operation,
  amount:               BigDecimal,
  transactionDate:      LocalDate,
  accountingDate:       LocalDate,
  balanceAfter:         BigDecimal
)

object Transaction {
  implicit val format: OFormat[Transaction] = Json.format[Transaction]
}

case class Transactions(transactions: Seq[Transaction])

object Transactions {

  implicit val format: OFormat[Transactions] = Json.format[Transactions]
}