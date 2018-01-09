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

import org.scalatestplus.play.PortNumber
import play.api.Application
import play.api.test.{Helpers, TestServer}

/**
  * Allows a Play test server to be provided to tests in a
  * [[http://www.scalatest.org/user_guide/sharing_fixtures#loanFixtureMethods loan-fixture]]
  * style.
  *
  * Useful when different tests need different configuration. When all tests in
  * a suite can share the same test server it's better to use [[org.scalatestplus.play.guice.GuiceOneServerPerSuite]]
  * which will make the tests run faster by having them share a test server
  * instead of starting/stopping a test server for each test.
  */
trait WithTestServer {

  def withTestServer[R](app: Application)(testCode: (Application, PortNumber) => R): R = {
    val port: Int = Helpers.testServerPort
    Helpers.running(TestServer(port, app)) {
      testCode(app, PortNumber(port))
    }
  }

}
