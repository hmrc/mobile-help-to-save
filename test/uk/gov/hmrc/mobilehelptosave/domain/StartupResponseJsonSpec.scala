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

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.Json

class StartupResponseJsonSpec extends WordSpec with Matchers {

  "EnabledStartupResponse JSON" should {
    """include "enabled": true""" in {
      val response = EnabledStartupResponse(
        Shuttering(shuttered = false, "", ""),
        None, None, None, None, None,
        balanceEnabled = false, paidInThisMonthEnabled = false, firstBonusEnabled = false,
        shareInvitationEnabled = false, savingRemindersEnabled = false
      )
      val json = Json.toJson(response)
      (json \ "enabled").as[Boolean] shouldBe true
    }
  }

  "DisabledStartupResponse JSON" should {
    """include "enabled": false""" in {
      val json = Json.toJson(DisabledStartupResponse)
      (json \ "enabled").as[Boolean] shouldBe false
    }
  }

}
