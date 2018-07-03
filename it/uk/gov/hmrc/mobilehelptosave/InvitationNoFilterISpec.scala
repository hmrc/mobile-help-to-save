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

import org.joda.time.DateTime
import org.scalatest.{Matchers, WordSpec}
import play.api.Application
import play.api.inject.bind
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mobilehelptosave.services.{Clock, FixedFakeClock}
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub}
import uk.gov.hmrc.mobilehelptosave.support.{MongoTestCollectionsDropAfterAll, OneServerPerSuiteWsClient, WireMockSupport}

class InvitationNoFilterISpec extends WordSpec with Matchers
  with FutureAwaits with DefaultAwaitTimeout with InvitationCleanup
  with WireMockSupport with MongoTestCollectionsDropAfterAll with OneServerPerSuiteWsClient {

  private val fixedClock = new FixedFakeClock(DateTime.parse("2018-02-08T12:34:56.000Z"))

  override implicit lazy val app: Application = appBuilder
    .configure(
      InvitationConfig.Enabled,
      "helpToSave.invitationFilters.workingTaxCredits" -> "false"
    )
    .overrides(bind[Clock].toInstance(fixedClock))
    .build()

  private val generator = new Generator(0)
  private val nino = generator.nextNino

  "GET /mobile-help-to-save/startup" when {
    "no invitation filters are enabled" should {
      "return user.state = InvitedFirstTime and then user.state = Invited when user is not already enrolled" in {
        AuthStub.userIsLoggedIn(internalAuthId, nino)
        HelpToSaveStub.currentUserIsNotEnrolled()

        val response1 = await(wsUrl("/mobile-help-to-save/startup").get())
        response1.status shouldBe 200
        (response1.json \ "user" \ "state").asOpt[String] shouldBe Some("InvitedFirstTime")

        val response2 = await(wsUrl("/mobile-help-to-save/startup").get())
        response2.status shouldBe 200
        (response2.json \ "user" \ "state").asOpt[String] shouldBe Some("Invited")
      }
    }
  }
}
