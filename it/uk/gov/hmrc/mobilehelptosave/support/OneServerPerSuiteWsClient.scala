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

package uk.gov.hmrc.mobilehelptosave.support

import org.scalatest.TestSuite
import org.scalatestplus.play.components.OneServerPerSuiteWithComponents
import org.scalatestplus.play.{PortNumber, WsScalaTestClient}
import play.api.libs.ws.WSClient

/**
  * Provides implicits so that the server started by OneServerPerSuite can
  * be called using the methods in WsScalaTestClient
  */
trait OneServerPerSuiteWsClient extends OneServerPerSuiteWithComponents with ComponentSupport with WsScalaTestClient {
  this: TestSuite =>
  implicit lazy val implicitPortNumber: PortNumber = portNumber
  implicit lazy val wsClient:           WSClient   = components.wsClient
}
