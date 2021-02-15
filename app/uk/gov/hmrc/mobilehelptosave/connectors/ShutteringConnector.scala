/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.mobilehelptosave.connectors

import com.google.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.http.{CoreGet, HeaderCarrier, Upstream5xxResponse}
import uk.gov.hmrc.mobilehelptosave.config.ShutteringConnectorConfig
import uk.gov.hmrc.mobilehelptosave.domain.Shuttering

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ShutteringConnector @Inject() (
  http:   CoreGet,
  config: ShutteringConnectorConfig) {

  def getShutteringStatus(
    journeyId:              String
  )(implicit headerCarrier: HeaderCarrier,
    ex:                     ExecutionContext
  ): Future[Shuttering] =
    http
      .GET[Shuttering](
        s"${config.shutteringBaseUrl}/mobile-shuttering/service/mobile-help-to-save/shuttered-status?journeyId=$journeyId"
      )
      .map(s => s)
      .recover {
        case e: Upstream5xxResponse => {
          Logger.warn(s"Internal Server Error received from mobile-shuttering:\n $e \nAssuming unshuttered.")
          Shuttering.shutteringDisabled
        }

        case e => {
          Logger.warn(s"Call to mobile-shuttering failed:\n $e \nAssuming unshuttered.")
          Shuttering.shutteringDisabled
        }
      }
}
