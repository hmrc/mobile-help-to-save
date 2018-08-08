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
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.api.domain.Registration

object ServiceLocatorStub {

  def registerShouldHaveBeenCalled(serviceName: String, serviceUrl: String): Unit =
    verify(1, registrationPattern(serviceName, serviceUrl))

  def registerShouldNotHaveBeenCalled(serviceName: String, serviceUrl: String): Unit =
    verify(0, registrationPattern(serviceName, serviceUrl))

  def registrationSucceeds(): Unit =
    stubFor(post(urlPathEqualTo("/registration"))
      .willReturn(aResponse()
        .withStatus(204)))

  private def regPayloadStringFor(serviceName: String, serviceUrl: String): String =
    Json.toJson(Registration(serviceName, serviceUrl, Some(Map("third-party-api" -> "true")))).toString

  private def registrationPattern(serviceName: String, serviceUrl: String): RequestPatternBuilder = postRequestedFor(urlPathEqualTo("/registration"))
    .withHeader("content-type", equalTo("application/json"))
    .withRequestBody(equalTo(regPayloadStringFor(serviceName, serviceUrl)))

}
