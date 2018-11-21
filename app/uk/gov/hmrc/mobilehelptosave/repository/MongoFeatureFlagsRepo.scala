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

import javax.inject.{Inject, Provider, Singleton}
import play.api.libs.json.{Format, Json, OWrites, Reads}
import play.modules.reactivemongo.ReactiveMongoComponent

import scala.concurrent.ExecutionContext

case class FeatureFlagsMongoModel(nino: String, savingsTargetsEnabled: Boolean)

object FeatureFlagsMongoModel {
  implicit val reads : Reads[FeatureFlagsMongoModel]   = Json.reads[FeatureFlagsMongoModel]
  implicit val writes: OWrites[FeatureFlagsMongoModel] = Json.writes[FeatureFlagsMongoModel]

  implicit val mongoFormats: Format[FeatureFlagsMongoModel] =
    Format(reads, writes)
}

@Singleton
class MongoFeatureFlagsRepo @Inject()(
  override val reactiveMongo: Provider[ReactiveMongoComponent]
)
  (implicit ec: ExecutionContext, mongoFormats: Format[FeatureFlagsMongoModel])
  extends NinoIndexedMongoRepo[FeatureFlagsMongoModel]("featureFlags", reactiveMongo)
    with FeatureFlagsRepo
