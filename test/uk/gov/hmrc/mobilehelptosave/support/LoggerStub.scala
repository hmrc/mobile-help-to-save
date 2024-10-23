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

import org.mockito.Mockito.when
import org.scalatest.OneInstancePerTest
import org.scalatestplus.mockito.MockitoSugar.mock
import org.slf4j.Logger
import play.api.LoggerLike
import uk.gov.hmrc.mobilehelptosave.connectors.HttpClientV2Helper

trait LoggerStub  {

  // when https://github.com/paulbutcher/ScalaMock/issues/39 is fixed we will be able to simplify this code by mocking LoggerLike directly (instead of slf4j.Logger)

  protected val slf4jLoggerStub: Logger = mock[Logger]

  when(slf4jLoggerStub.isWarnEnabled).thenReturn(true)
  when(slf4jLoggerStub.isInfoEnabled).thenReturn(true)

  protected val logger: LoggerLike = new LoggerLike {
    override val logger: Logger = slf4jLoggerStub

  }
}
