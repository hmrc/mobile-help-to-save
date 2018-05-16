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

import com.google.inject.name.Named
import javax.inject.{Inject, Singleton}
import play.api.libs.json._

sealed trait StartupResponse

final case class EnabledStartupResponse(
  shuttering: Shuttering,
  infoUrl: Option[String],
  invitationUrl: Option[String],
  accessAccountUrl: Option[String],
  user: Option[UserDetails],
  userError: Option[ErrorInfo],
  balanceEnabled: Boolean,
  paidInThisMonthEnabled: Boolean,
  firstBonusEnabled: Boolean,
  shareInvitationEnabled: Boolean,
  savingRemindersEnabled: Boolean
) extends StartupResponse

@Singleton
case class Shuttering @Inject() (
  @Named("helpToSave.shuttering.shuttered") shuttered: Boolean,
  @Named("helpToSave.shuttering.title") title: String,
  @Named("helpToSave.shuttering.message") message: String
)

case object Shuttering {
  implicit val writes: Writes[Shuttering] = Json.writes[Shuttering]
}

case object DisabledStartupResponse extends StartupResponse

object StartupResponse {
  implicit val writes: Writes[StartupResponse] = new Writes[StartupResponse] {
    override def writes(o: StartupResponse): JsValue = o match {
      case e: EnabledStartupResponse => enabledWrites.writes(e)
      case d: DisabledStartupResponse.type => disabledWrites.writes(d)
    }
  }

  private val enabledWrites: Writes[EnabledStartupResponse] = Json.writes[EnabledStartupResponse]
    .transform((_: JsObject) + ("enabled" -> JsBoolean(true)))

  private val disabledWrites: Writes[DisabledStartupResponse.type] = new Writes[DisabledStartupResponse.type] {
    override def writes(o: DisabledStartupResponse.type): JsValue = Json.obj("enabled" -> false)
  }

}
