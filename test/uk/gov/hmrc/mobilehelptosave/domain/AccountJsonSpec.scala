/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.libs.json.Json
import uk.gov.hmrc.mobilehelptosave.AccountTestData
import uk.gov.hmrc.mobilehelptosave.support.BaseSpec

class AccountJsonSpec extends BaseSpec with AccountTestData {

  "Account JSON" should {
    "format currentBonusTerm as documented in the README" in {
      (Json.toJson(mobileHelpToSaveAccount.copy(currentBonusTerm = CurrentBonusTerm.First)) \ "currentBonusTerm")
        .as[String] mustBe "First"
      (Json.toJson(mobileHelpToSaveAccount.copy(currentBonusTerm = CurrentBonusTerm.Second)) \ "currentBonusTerm")
        .as[String] mustBe "Second"
      (Json.toJson(mobileHelpToSaveAccount.copy(currentBonusTerm = CurrentBonusTerm.AfterFinalTerm)) \ "currentBonusTerm")
        .as[String] mustBe "AfterFinalTerm"
    }
  }
}
