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

package uk.gov.hmrc.mobilehelptosave.repos

import reactivemongo.api.commands.{WriteConcern, WriteResult}
import reactivemongo.core.errors.DatabaseException

import scala.concurrent.{ExecutionContext, Future}

/**
  * A repository that can be tested by [[uk.gov.hmrc.mobilehelptosave.repos.RepositorySpec]]
  * @tparam A the entity that the repository persists
  */
trait TestableRepository[A <: Any, ID <: Any] {

  import reactivemongo.api.ReadPreference

  def findById(id: ID, readPreference: ReadPreference = ReadPreference.primaryPreferred)(implicit ec: ExecutionContext): Future[Option[A]]

  def removeById(id: ID, writeConcern: WriteConcern = WriteConcern.Default)(implicit ec: ExecutionContext): Future[WriteResult]

  def insert(entity: A)(implicit ec: ExecutionContext): Future[WriteResult]

  def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]]

  def isDuplicateKey(e: DatabaseException): Boolean = e.code.contains(11000)

}
