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
import org.joda.time.DateTime
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.{DefaultWriteResult, WriteConcern, WriteResult}
import reactivemongo.core.errors.GenericDatabaseException
import uk.gov.hmrc.mobilehelptosave.domain.{InternalAuthId, Invitation}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

class FakeInvitationRepository extends InvitationRepository {
  private val store = mutable.Map[InternalAuthId, Invitation]()

  override def findById(id: InternalAuthId, readPreference: ReadPreference)(implicit ec: ExecutionContext): Future[Option[Invitation]] =
    Future successful store.get(id)

  override def countCreatedSince(dateTime: DateTime)(implicit ec: ExecutionContext): Future[Int] = Future.successful {
    store.values.count { i => i.created.isEqual(dateTime) || i.created.isAfter(dateTime) }
  }

  override def insert(entity: Invitation)(implicit ec: ExecutionContext): Future[WriteResult] = Future {
    if (store.contains(entity.internalAuthId)) {
      throw GenericDatabaseException("already exists", Some(11000))
    } else {
      store.put(entity.internalAuthId, entity)
      DefaultWriteResult(ok = true, 1, Seq.empty, None, None, None)
    }
  }

  override def removeById(id: InternalAuthId, writeConcern: WriteConcern)(implicit ec: ExecutionContext): Future[WriteResult] = Future successful {
    val removed = store.remove(id).isDefined

    DefaultWriteResult(ok = true, n = if (removed) 1 else 0, Seq.empty, None, None, None)
  }

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = Future successful Seq.empty
}
