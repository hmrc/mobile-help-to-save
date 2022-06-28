/*
 * Copyright 2022 HM Revenue & Customs
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

import cats.instances.future._
import cats.syntax.functor._
import com.mongodb.client.model.Indexes.text
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import org.mongodb.scala.model.Indexes.descending
import play.api.libs.json._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.domain.Eligibility
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

trait EligibilityRepo[F[_]] {
  def setEligibility(eligibility: Eligibility): F[Unit]

  def getEligibility(nino: Nino): F[Option[Eligibility]]
}

class MongoEligibilityRepo(
  mongo:        MongoComponent
)(implicit ec:  ExecutionContext,
  mongoFormats: Format[Eligibility])
    extends PlayMongoRepository[Eligibility](collectionName = "eligibility",
                                             mongoComponent = mongo,
                                             domainFormat   = mongoFormats,
                                             indexes = Seq(
                                               IndexModel(descending("expireAt"),
                                                          IndexOptions()
                                                            .name("expireAtIdx")
                                                            .expireAfter(0, TimeUnit.SECONDS)),
                                               IndexModel(text("nino"),
                                                          IndexOptions().name("ninoIdx").unique(true).sparse(true))
                                             ),
                                             replaceIndexes = true)
    with EligibilityRepo[Future] {

  override def setEligibility(eligibility: Eligibility): Future[Unit] =
    collection.insertOne(eligibility).toFuture().void

  override def getEligibility(nino: Nino): Future[Option[Eligibility]] =
    collection.find(equal("nino", Codecs.toBson(nino))).headOption()
}
