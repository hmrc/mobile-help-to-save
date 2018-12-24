package uk.gov.hmrc.mobilehelptosave

import org.scalatest.{Matchers, WordSpec}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.mobilehelptosave.support.OneServerPerSuiteWsClient


/**
  * Need two separate tests so that the servers can be run with different system
  * property settings for the router
  */
class TestOnlyRoutesNotWiredISpec extends WordSpec with Matchers
  with FutureAwaits with DefaultAwaitTimeout
  with OneServerPerSuiteWsClient {
  val clearGoalEventsUrl = "/mobile-help-to-save/test-only/clear-goal-events"
  private val applicationRouterKey = "application.router"

  System.clearProperty(applicationRouterKey)

  s"GET $clearGoalEventsUrl  without '" + applicationRouterKey + "' set" should {
    "Return 404" in (await(wsUrl(clearGoalEventsUrl).get).status shouldBe 404)
  }
}


class TestOnlyRoutesWiredISpec extends WordSpec with Matchers
  with FutureAwaits with DefaultAwaitTimeout
  with OneServerPerSuiteWsClient {
  val clearGoalEventsUrl = "/mobile-help-to-save/test-only/clear-goal-events"

  private val applicationRouterKey = "application.router"
  private val testOnlyRoutes       = "testOnlyDoNotUseInAppConf.Routes"

  System.setProperty(applicationRouterKey, testOnlyRoutes)

  s"GET $clearGoalEventsUrl with $applicationRouterKey set to $testOnlyRoutes" should {
    s"Return 200 " in (await(wsUrl(clearGoalEventsUrl).get).status shouldBe 200)
  }
}
