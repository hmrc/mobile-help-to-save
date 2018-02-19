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

package uk.gov.hmrc.mobilehelptosave

import java.util.UUID

import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.play.ServerProvider
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.mobilehelptosave.domain.InternalAuthId
import uk.gov.hmrc.mobilehelptosave.repos.InvitationRepository

import scala.concurrent.ExecutionContext.Implicits.global

trait InvitationCleanup extends BeforeAndAfterEach { this: (Suite with ServerProvider with FutureAwaits with DefaultAwaitTimeout) =>

  protected def internalAuthId: InternalAuthId = _internalAuthId

  private var _internalAuthId: InternalAuthId = _

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    _internalAuthId = new InternalAuthId(s"test-${UUID.randomUUID()}}")
  }

  override protected def afterEach(): Unit = {
    await(app.injector.instanceOf[InvitationRepository].removeById(_internalAuthId))
    super.afterEach()
  }

}
