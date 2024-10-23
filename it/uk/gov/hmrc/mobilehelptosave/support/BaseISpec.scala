/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.mobilehelptosave.support

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.ws.WSRequest
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.mobilehelptosave.TransactionTestData


trait BaseISpec
    extends AnyWordSpecLike
    with Matchers
    with TransactionTestData
    with FutureAwaits
    with DefaultAwaitTimeout
    with WireMockSupport
    with OneServerPerSuiteWsClient {

  val acceptJsonHeader:        (String, String) = "Accept"        -> "application/vnd.hmrc.1.0+json"
  val authorisationJsonHeader: (String, String) = "AUTHORIZATION" -> "Bearer 123"
  val journeyId = "27085215-69a4-4027-8f72-b04b10ec16b0"

  val generator = new Generator(0)
  val nino: Nino = generator.nextNino

  def requestWithAuthHeaders(url: String): WSRequest = {
    wsUrl(url).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
  }
}
