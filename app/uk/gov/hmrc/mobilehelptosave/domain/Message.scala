/*
 * Copyright 2019 HM Revenue & Customs
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
import java.time.LocalDateTime

import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.domain.Nino

case class Message(
  messageId:   String = BSONObjectID.generate().stringify,
  nino:        Nino,
  messageType: MessageType,
  messageKey:  MessageKey,
  isSeen:      Boolean = false,
  date:        LocalDateTime = LocalDateTime.now()) {

  def toApiMessage: ApiMessage =
    ApiMessage(
      messageId   = messageId,
      messageType = messageType,
      message = messageKey match {
        case BalanceReached200  => "Your balance has reached £200. That's great!"
        case BalanceReached500  => "Your balance has reached £500. That's great!"
        case BalanceReached750  => "Your balance has reached £750. That's great!"
        case BalanceReached1500 => "Your balance has reached £1000. That's great!"
        case BalanceReached2000 => "Your balance has reached £2000. That's great!"
        case BalanceReached2400 => "Your balance has reached £2400. That's great!"
      },
      isSeen = isSeen,
      date   = date
    )
}

object Message {
  implicit val formats: OFormat[Message] = Json.format
}

case class ApiMessage(messageId: String, messageType: MessageType, message: String, isSeen: Boolean, date: LocalDateTime)

object ApiMessage {
  implicit val formats: OFormat[ApiMessage] = Json.format
}

sealed trait MessageType

case object BalanceReached extends MessageType

object MessageType {
  implicit val messageTypeFormat: Format[MessageType] = new Format[MessageType] {
    override def reads(json: JsValue): JsResult[MessageType] = json.as[String] match {
      case "BalanceReached" => JsSuccess(BalanceReached)
      case _                => JsError("Invalid message type")
    }
    override def writes(messageType: MessageType): JsString = JsString(messageType.toString)
  }
}

sealed trait MessageKey

case object BalanceReached200 extends MessageKey
case object BalanceReached500 extends MessageKey
case object BalanceReached750 extends MessageKey
case object BalanceReached1500 extends MessageKey
case object BalanceReached2000 extends MessageKey
case object BalanceReached2400 extends MessageKey

object MessageKey {
  implicit val messageTypeFormat: Format[MessageKey] = new Format[MessageKey] {
    override def reads(json: JsValue): JsResult[MessageKey] = json.as[String] match {
      case "BalanceReached200"  => JsSuccess(BalanceReached200)
      case "BalanceReached500"  => JsSuccess(BalanceReached500)
      case "BalanceReached750"  => JsSuccess(BalanceReached750)
      case "BalanceReached1500" => JsSuccess(BalanceReached1500)
      case "BalanceReached2000" => JsSuccess(BalanceReached2000)
      case "BalanceReached2400" => JsSuccess(BalanceReached2400)
      case _                    => JsError("Invalid message key")
    }
    override def writes(messageKey: MessageKey): JsString = JsString(messageKey.toString)
  }
}
