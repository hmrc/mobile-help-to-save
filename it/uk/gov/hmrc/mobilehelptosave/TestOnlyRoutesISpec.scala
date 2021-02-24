package uk.gov.hmrc.mobilehelptosave

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.Json
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.mobilehelptosave.domain.TestSavingsGoal
import uk.gov.hmrc.mobilehelptosave.support.OneServerPerSuiteWsClient

import java.time.LocalDate

/**
  * Need two separate tests so that the servers can be run with different system
  * property settings for the router
  */
class TestOnlyRoutesNotWiredISpec
    extends WordSpec
    with Matchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with OneServerPerSuiteWsClient {
  val clearGoalEventsUrl           = "/mobile-help-to-save/test-only/clear-goal-events"
  private val applicationRouterKey = "application.router"

  System.clearProperty(applicationRouterKey)

  s"GET $clearGoalEventsUrl  without '" + applicationRouterKey + "' set" should {
    "Return 404" in (await(wsUrl(clearGoalEventsUrl).get).status shouldBe 404)
  }
}

class TestOnlyRoutesWiredISpec
    extends WordSpec
    with Matchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with OneServerPerSuiteWsClient {
  val generator          = new Generator(0)
  val nino               = generator.nextNino.nino
  val clearGoalEventsUrl = "/mobile-help-to-save/test-only/clear-goal-events"
  val getGoalEventsUrl   = s"/mobile-help-to-save/test-only/goal-events/$nino"
  val clearMiletonesUrl  = "/mobile-help-to-save/test-only/clear-milestone-data"
  val createGoalUrl      = "/mobile-help-to-save/test-only/create-goal"

  private val applicationRouterKey = "application.router"
  private val testOnlyRoutes       = "testOnlyDoNotUseInAppConf.Routes"

  System.setProperty(applicationRouterKey, testOnlyRoutes)

  s"GET $clearGoalEventsUrl with $applicationRouterKey set to $testOnlyRoutes" should {
    s"Return 200 " in (await(wsUrl(clearGoalEventsUrl).get).status shouldBe 200)
  }

  s"GET $getGoalEventsUrl with $applicationRouterKey set to $testOnlyRoutes" should {
    s"Return 200 " in (await(wsUrl(getGoalEventsUrl).get).status shouldBe 200)
  }

  s"GET $clearMiletonesUrl with $applicationRouterKey set to $testOnlyRoutes" should {
    s"Return 200 " in (await(wsUrl(clearMiletonesUrl).get).status shouldBe 200)
  }

  s"PUT $createGoalUrl with $applicationRouterKey set to $testOnlyRoutes" should {
    s"Return 201 " in {
      (await(
        wsUrl(createGoalUrl)
          .put(Json.toJson(TestSavingsGoal(Nino(nino), Some(10.0), None, LocalDate.now().minusMonths(8))))
      ).status                                    shouldBe 201)
      await(wsUrl(clearGoalEventsUrl).get).status shouldBe 200
    }
  }
}
