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

package uk.gov.hmrc.mobilehelptosave.repository

import java.time.LocalDateTime

import play.api.libs.json.{Format, Json, OWrites, Reads}
import uk.gov.hmrc.domain.Nino

case class SavingsGoalRepoModel(nino: Nino, amount: Double, createdAt: LocalDateTime)

object SavingsGoalRepoModel {
  implicit val reads : Reads[SavingsGoalRepoModel]   = Json.reads[SavingsGoalRepoModel]
  implicit val writes: OWrites[SavingsGoalRepoModel] = Json.writes[SavingsGoalRepoModel]

  implicit val format: Format[SavingsGoalRepoModel] =
    Format(reads, writes)
}

