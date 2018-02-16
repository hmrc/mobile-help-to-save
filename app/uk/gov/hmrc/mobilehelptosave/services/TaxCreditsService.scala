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

package uk.gov.hmrc.mobilehelptosave.services

import javax.inject.{Inject, Singleton}

import cats.data.OptionT
import cats.instances.future._
import com.google.inject.ImplementedBy
import play.api.LoggerLike
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.connectors.TaxCreditsBrokerConnector

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[TaxCreditsServiceImpl])
trait TaxCreditsService {
  def hasRecentWtcPayments(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]]
}

@Singleton
class TaxCreditsServiceImpl @Inject() (
  logger: LoggerLike,
  taxCreditsBrokerConnector: TaxCreditsBrokerConnector,
  clock: Clock)
  extends TaxCreditsService {

  override def hasRecentWtcPayments(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] =
    OptionT(taxCreditsBrokerConnector.previousPayments(nino)(hc, ec)).map { previousPayments =>
      previousPayments.filter(p => !p.paymentDate.isBefore(clock.now())) foreach(p =>
        logger.warn(s"""Previous payment has date that isn't in the past: "${p.paymentDate}""""))
      val thirtyDaysAgo = clock.now().minusDays(30)
      previousPayments.exists(p =>
        p.amount > 0 &&
        (p.paymentDate.isAfter(thirtyDaysAgo) || p.paymentDate.isEqual(thirtyDaysAgo))
      )
    }.value

}
