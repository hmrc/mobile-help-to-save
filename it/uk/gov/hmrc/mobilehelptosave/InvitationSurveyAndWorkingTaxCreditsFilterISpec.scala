package uk.gov.hmrc.mobilehelptosave

import org.joda.time.DateTime
import org.scalatest.{Matchers, WordSpec}
import play.api.Application
import play.api.inject._
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mobilehelptosave.services.{Clock, FixedFakeClock}
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub, NativeAppWidgetStub, TaxCreditBrokerStub}
import uk.gov.hmrc.mobilehelptosave.support.{OneServerPerSuiteWsClient, WireMockSupport}

class InvitationSurveyAndWorkingTaxCreditsFilterISpec extends WordSpec with Matchers
  with FutureAwaits with DefaultAwaitTimeout with InvitationCleanup
  with WireMockSupport with OneServerPerSuiteWsClient {

  private val fixedClock = new FixedFakeClock(DateTime.parse("2018-02-08T12:34:56.000Z"))

  override implicit lazy val app: Application = wireMockApplicationBuilder()
    .configure(
      InvitationConfig.Enabled,
      "helpToSave.invitationFilters.survey" -> "true",
      "helpToSave.invitationFilters.workingTaxCredits" -> "true"
    )
    .overrides(bind[Clock].toInstance(fixedClock))
    .build()

  private val generator = new Generator(0)
  private val nino = generator.nextNino

  "GET /mobile-help-to-save/start" when {
    "both survey and working tax credit filter is enable" should {

      "return user.state = InvitedFirstTime when user indicated that they wanted to be contacted and has received a WTC payment recently" in {
        AuthStub.userIsLoggedIn(internalAuthId, nino)
        HelpToSaveStub.currentUserIsNotEnrolled()
        NativeAppWidgetStub.currentUserWantsToBeContacted()
        TaxCreditBrokerStub.userHasFeb2018WorkingTaxCreditsPayments(nino)

        val response1 = await(wsUrl("/mobile-help-to-save/startup").get())
        response1.status shouldBe 200
        (response1.json \ "user" \ "state").asOpt[String] shouldBe Some("InvitedFirstTime")

        val response2 = await(wsUrl("/mobile-help-to-save/startup").get())
        response2.status shouldBe 200
        (response2.json \ "user" \ "state").asOpt[String] shouldBe Some("Invited")
      }

      "return user.state = NotEnrolled when user indicated that they dont wanted to be contacted and has received a WTC payment recently" in {
        AuthStub.userIsLoggedIn(internalAuthId, nino)
        HelpToSaveStub.currentUserIsNotEnrolled()
        NativeAppWidgetStub.currentUserHasNotRespondedToSurvey()
        TaxCreditBrokerStub.userHasFeb2018WorkingTaxCreditsPayments(nino)

        val response = await(wsUrl("/mobile-help-to-save/startup").get())
        response.status shouldBe 200
        (response.json \ "user" \ "state").asOpt[String] shouldBe Some("NotEnrolled")
      }

      "return user.state = NotEnrolled when user indicated that they wanted to be contacted and has not received a WTC payment recently" in {
        AuthStub.userIsLoggedIn(internalAuthId, nino)
        HelpToSaveStub.currentUserIsNotEnrolled()
        NativeAppWidgetStub.currentUserWantsToBeContacted()
        TaxCreditBrokerStub.userHasNoWorkingTaxCreditsPayments(nino)

        val response = await(wsUrl("/mobile-help-to-save/startup").get())
        response.status shouldBe 200
        (response.json \ "user" \ "state").asOpt[String] shouldBe Some("NotEnrolled")
      }
    }
  }
}