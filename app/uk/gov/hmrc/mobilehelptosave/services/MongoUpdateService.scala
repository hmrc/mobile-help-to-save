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

package uk.gov.hmrc.mobilehelptosave.services

import cats.MonadError
import play.api.Logger
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.repository.{MongoMilestonesRepo, MongoPreviousBalanceRepo, MongoSavingsGoalEventRepo}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

trait MongoUpdateService[F[_]] {

  def updateExpireAtByNino(
    nino:     Nino,
    expireAt: LocalDateTime
  ): F[Unit]

}

class HtsMongoUpdateService[F[_]](
  mongoMilestonesRepo:       MongoMilestonesRepo,
  mongoSavingsGoalEventRepo: MongoSavingsGoalEventRepo,
  mongoPreviousBalanceRepo:  MongoPreviousBalanceRepo
)(implicit executionContext: ExecutionContext,
  F:                         MonadError[F, Throwable])
    extends MongoUpdateService[F] {

  override def updateExpireAtByNino(
    nino:     Nino,
    expireAt: LocalDateTime
  ): F[Unit] = {

    val logger: Logger = Logger(this.getClass)

    val processUpdates = for {
      updateRequired <- mongoPreviousBalanceRepo.getPreviousBalanceUpdateRequired(nino)
      _              <- if (updateRequired.isDefined) mongoMilestonesRepo.updateExpireAt(nino, expireAt) else Future successful ()
      _ <- if (updateRequired.isDefined) mongoSavingsGoalEventRepo.updateExpireAt(nino, expireAt)
          else Future successful ()
      _ <- if (updateRequired.isDefined) mongoPreviousBalanceRepo.updateExpireAt(nino, expireAt)
          else Future successful ()
    } yield (if (updateRequired.isDefined) logger.info("expireAt fields updated for user"))

    processUpdates.recover {
      case e => logger.warn("ExpireAt update failed: " + e)
    }

    F.pure(())

  }
}
