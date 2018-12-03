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

import javax.inject.{Inject, Provider}
import play.api.libs.json.{Format, Json, OWrites, Reads}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.domain.Nino

import scala.concurrent.{ExecutionContext, Future}

case class SavingsGoalMongoModel(nino: Nino, amount: Double, createdAt: LocalDateTime)

object SavingsGoalMongoModel {
  implicit val reads : Reads[SavingsGoalMongoModel]   = Json.reads[SavingsGoalMongoModel]
  implicit val writes: OWrites[SavingsGoalMongoModel] = Json.writes[SavingsGoalMongoModel]

  implicit val mongoFormats: Format[SavingsGoalMongoModel] =
    Format(reads, writes)
}

class MongoSavingsGoalRepo @Inject()(
  override val reactiveMongo: Provider[ReactiveMongoComponent]
)
  (implicit ec: ExecutionContext, mongoFormats: Format[SavingsGoalMongoModel])
  extends IndexedMongoRepo[Nino, SavingsGoalMongoModel]("savingsGoals", "nino", reactiveMongo)
    with SavingsGoalRepo {

  override def setGoal(nino: Nino, amount: Double): Future[Unit] = set(SavingsGoalMongoModel(nino, amount, LocalDateTime.now))(_.nino)
}
