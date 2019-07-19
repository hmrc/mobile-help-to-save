/*
 * Copyright 2019 HM Revenue & Customs
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

import org.scalamock.scalatest.MockFactory
import org.scalatest.OneInstancePerTest
import org.slf4j.Logger
import play.api.LoggerLike

trait LoggerStub { this: MockFactory with OneInstancePerTest =>

  // when https://github.com/paulbutcher/ScalaMock/issues/39 is fixed we will be able to simplify this code by mocking LoggerLike directly (instead of slf4j.Logger)
  protected val slf4jLoggerStub: Logger = stub[Logger]
  (slf4jLoggerStub.isWarnEnabled: () => Boolean).when().returning(true)
  (slf4jLoggerStub.isInfoEnabled: () => Boolean).when().returning(true)
  protected val logger: LoggerLike = new LoggerLike {
    override val logger: Logger = slf4jLoggerStub
  }

}
