package uk.gov.hmrc.mobilehelptosave

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Json
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.support.OneServerPerSuiteWsClient

import java.time.{LocalDate, LocalDateTime, ZoneOffset}

/**
  * Need two separate tests so that the servers can be run with different system
  * property settings for the router
  */
class TestOnlyRoutesNotWiredISpec
    extends AnyWordSpecLike
    with Matchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with OneServerPerSuiteWsClient {
  val clearGoalEventsUrl           = "/mobile-help-to-save/test-only/clear-goal-events"
  private val applicationRouterKey = "application.router"

  System.clearProperty(applicationRouterKey)

  s"GET $clearGoalEventsUrl  without '" + applicationRouterKey + "' set" should {
    "Return 404" in (await(wsUrl(clearGoalEventsUrl).get()).status shouldBe 404)
  }
}

class TestOnlyRoutesWiredISpec
    extends AnyWordSpecLike
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
  val addMilestoneUrl    = "/mobile-help-to-save/test-only/add-milestone"
  val addMilestonesUrl   = "/mobile-help-to-save/test-only/add-milestones/10"

  private val applicationRouterKey = "application.router"
  private val testOnlyRoutes       = "testOnlyDoNotUseInAppConf.Routes"

  System.setProperty(applicationRouterKey, testOnlyRoutes)

  s"GET $clearGoalEventsUrl with $applicationRouterKey set to $testOnlyRoutes" should {
    s"Return 200 " in (await(wsUrl(clearGoalEventsUrl).get()).status shouldBe 200)
  }

  s"GET $getGoalEventsUrl with $applicationRouterKey set to $testOnlyRoutes" should {
    s"Return 200 " in (await(wsUrl(getGoalEventsUrl).get()).status shouldBe 200)
  }

  s"GET $clearMiletonesUrl with $applicationRouterKey set to $testOnlyRoutes" should {
    s"Return 200 " in (await(wsUrl(clearMiletonesUrl).get()).status shouldBe 200)
  }

  s"PUT $createGoalUrl with $applicationRouterKey set to $testOnlyRoutes" should {
    s"Return 201 " in {
      (await(
        wsUrl(createGoalUrl)
          .put(Json.toJson(TestSavingsGoal(Nino(nino), Some(10.0), None, LocalDate.now().minusMonths(8))))
      ).status                                    shouldBe 201)
      await(wsUrl(clearGoalEventsUrl).get()).status shouldBe 200
    }
  }

  s"PUT $addMilestoneUrl with $applicationRouterKey set to $testOnlyRoutes" should {
    s"Return 201 " in {
      (await(
        wsUrl(addMilestoneUrl)
          .put(
            Json.toJson(
              TestMilestone(
                Nino(nino),
                BonusReached,
                Milestone(FirstBonusReached150),
                true,
                false,
                Some(LocalDateTime.now().minusMonths(1).toInstant(ZoneOffset.UTC)),
                Some(LocalDateTime.now().plusMinutes(10).toInstant(ZoneOffset.UTC))
              )
            )
          )
      ).status shouldBe 201)
    }
  }

  s"PUT $addMilestonesUrl with $applicationRouterKey set to $testOnlyRoutes" should {
    s"Return 201 " in {
      (await(
        wsUrl(addMilestonesUrl)
          .put(
            Json.toJson(
              TestMilestone(
                Nino(nino),
                BonusReached,
                Milestone(FirstBonusReached150),
                true,
                false,
                Some(LocalDateTime.now().minusMonths(1).toInstant(ZoneOffset.UTC)),
                Some(LocalDateTime.now().plusMinutes(10).toInstant(ZoneOffset.UTC))
              )
            )
          )
      ).status shouldBe 201)
    }
  }
}
