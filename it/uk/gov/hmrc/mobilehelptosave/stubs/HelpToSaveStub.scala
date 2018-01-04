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

package uk.gov.hmrc.mobilehelptosave.stubs

import com.github.tomakehurst.wiremock.client.WireMock._

object HelpToSaveStub {
  def currentUserIsEnrolled(): Unit = enrolmentStatusIs(true)
  def currentUserIsNotEnrolled(): Unit = enrolmentStatusIs(false)

  def enrolmentStatusReturnsInternalServerError(): Unit =
    stubFor(get(urlPathEqualTo("/help-to-save/enrolment-status"))
      .willReturn(aResponse()
        .withStatus(500)))

  private def enrolmentStatusIs(status: Boolean): Unit =
    stubFor(get(urlPathEqualTo("/help-to-save/enrolment-status"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""{"enrolled":$status}"""
        )))
}
