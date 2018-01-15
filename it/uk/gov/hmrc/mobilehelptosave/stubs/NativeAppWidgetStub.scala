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

object NativeAppWidgetStub {

  def currentUserHasNotRespondedToSurvey(): Unit =
    stubFor(get(urlPathEqualTo("/native-app-widget/widget-data/HELP_TO_SAVE_1/question_3"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          """[]"""
        )))

  def currentUserWantsToBeContacted(): Unit =
    stubFor(get(urlPathEqualTo("/native-app-widget/widget-data/HELP_TO_SAVE_1/question_3"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          """
            |[
            |  {
            |    "content": "Yes",
            |    "contentType": "String",
            |    "additionalInfo": "Would you like us to contact you?"
            |  }
            |]
          """.stripMargin
        )))

  def gettingAnswersReturnsInternalServerError(): Unit =
      stubFor(get(urlPathMatching("/native-app-widget/widget-data/[^/]+/[^/]+"))
      .willReturn(aResponse()
        .withStatus(500)))

}
