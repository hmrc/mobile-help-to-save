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

import javax.inject.{Inject, Singleton}
import play.api.LoggerLike
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class DropInvitationsTask @Inject() (
  logger: LoggerLike,
  invitationRepository: InvitationMongoRepository,
  ninoWithoutWtcRepository: NinoWithoutWtcMongoRepository
) {

  // fire-and-forget (don't wait for future) - app will start even if this task fails but this task's success/failure will be logged
  dropCollections()

  private def dropCollections(): Future[Unit] = {
    for {
      _ <- dropCollection(invitationRepository)
      _ <- dropCollection(ninoWithoutWtcRepository)
    } yield ()
  }

  private def dropCollection(repository: ReactiveRepository[_, _]): Future[Unit] = {
    for {
      docsBefore <- repository.count
      _ = logger.warn(s"About to drop ${repository.collection.name} collection containing $docsBefore documents")
      dropSuccess <- repository.drop
      docsAfter <- repository.count
      _ = logger.warn(s"Drop ${repository.collection.name} success = $dropSuccess, documents remaining: $docsAfter (should be 0)")
    } yield ()
  }

}
