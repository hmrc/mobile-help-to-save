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
import reactivemongo.api.commands.{WriteConcern, WriteResult}
import uk.gov.hmrc.mobilehelptosave.domain.{InternalAuthId, Invitation}

import scala.concurrent.{ExecutionContext, Future}

class FakeInvitationRepository extends InvitationRepository with FakeRepository[Invitation, InternalAuthId] {

  override protected def idOf(entity: Invitation): InternalAuthId = entity.internalAuthId

  override def countCreatedSince(dateTime: DateTime)(implicit ec: ExecutionContext): Future[Int] = Future.successful {
    store.values.count { i => i.created.isEqual(dateTime) || i.created.isAfter(dateTime) }
  }

}

object ShouldNotBeCalledInvitationRepository extends InvitationRepository {
  private def dontCallMe() = Future failed new RuntimeException("InvitationRepository should not be called in this situation")

  override def findById(id: InternalAuthId, readPreference: ReadPreference)(implicit ec: ExecutionContext): Future[Option[Invitation]] = dontCallMe()

  override def countCreatedSince(dateTime: DateTime)(implicit ec: ExecutionContext): Future[Int] = dontCallMe()

  override def insert(entity: Invitation)(implicit ec: ExecutionContext): Future[WriteResult] = dontCallMe()

  override def removeById(id: InternalAuthId, writeConcern: WriteConcern)(implicit ec: ExecutionContext): Future[WriteResult] = dontCallMe()

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = dontCallMe()
}
