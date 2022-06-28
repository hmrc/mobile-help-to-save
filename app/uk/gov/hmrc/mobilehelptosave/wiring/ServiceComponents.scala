/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.mobilehelptosave.wiring

import cats.implicits._
import com.kenshoo.play.metrics._
import com.softwaremill.macwire.wire
import controllers.AssetsComponents
import play.api.ApplicationLoader.Context
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router
import play.api.{BuiltInComponentsFromContext, Logger, LoggerLike}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.mobilehelptosave.api.DocumentationController
import uk.gov.hmrc.mobilehelptosave.config.{MobileHelpToSaveConfig, MongoConfig}
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveConnectorImpl, ShutteringConnector}
import uk.gov.hmrc.mobilehelptosave.controllers._
import uk.gov.hmrc.mobilehelptosave.controllers.test.TestController
import uk.gov.hmrc.mobilehelptosave.repository._
import uk.gov.hmrc.mobilehelptosave.sandbox.SandboxData
import uk.gov.hmrc.mobilehelptosave.services._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.config._
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import uk.gov.hmrc.play.health.HealthController

import scala.concurrent.{ExecutionContext, Future}

class ServiceComponents(context: Context)
    extends BuiltInComponentsFromContext(context)
    with AhcWSComponents
    with SandboxRequestRouting
    with AssetsComponents
    with FilterWiring {

  implicit val ec: ExecutionContext = actorSystem.dispatcher

  lazy val prodLogger: LoggerLike = Logger(this.getClass)

  lazy val prefix:           String                           = "/"
  lazy val sandboxRouter:    sandbox.Routes                   = wire[sandbox.Routes]
  lazy val definitionRouter: definition.Routes                = wire[definition.Routes]
  lazy val healthRouter:     health.Routes                    = wire[health.Routes]
  lazy val appRouter:        app.Routes                       = wire[app.Routes]
  lazy val apiRouter:        api.Routes                       = wire[api.Routes]
  lazy val testRouter:       _root_.test.Routes               = wire[_root_.test.Routes]
  lazy val prodRoutes:       prod.Routes                      = wire[prod.Routes]
  lazy val testOnlyRoutes:   testOnlyDoNotUseInAppConf.Routes = wire[testOnlyDoNotUseInAppConf.Routes]

  override lazy val router: Router =
    if (System.getProperty("application.router") == classOf[testOnlyDoNotUseInAppConf.Routes].getName) {
      prodLogger.info("Wiring in test-only routes")
      testOnlyRoutes
    } else prodRoutes

  val reportingService: ReportingService = wire[ReportingService]

  lazy val servicesConfig: ServicesConfig = wire[ServicesConfig]

  lazy val ws: DefaultHttpClient = wire[DefaultHttpClient]

  lazy val clock: Clock = wire[ClockImpl]

  lazy val sandboxData: SandboxData = wire[SandboxData]

  lazy val authorisedWithIds: AuthorisedWithIds = wire[AuthorisedWithIdsImpl]

  lazy val helpToSaveConfig: MobileHelpToSaveConfig = wire[MobileHelpToSaveConfig]

  lazy val helpToSaveConnector: HelpToSaveConnectorImpl = wire[HelpToSaveConnectorImpl]
  lazy val shutteringConnector: ShutteringConnector     = wire[ShutteringConnector]

  lazy val authConnector: AuthConnector = wire[DefaultAuthConnector]

  lazy val userService:              UserService                      = wire[HtsUserService]
  lazy val savingsUpdateService:     SavingsUpdateService             = wire[HtsSavingsUpdateService]
  lazy val accountService:           AccountService[Future]           = wire[HtsAccountService[Future]]
  lazy val milestonesService:        MilestonesService[Future]        = wire[HtsMilestonesService[Future]]
  lazy val balanceMilestonesService: BalanceMilestonesService[Future] = wire[HtsBalanceMilestonesService[Future]]
  lazy val mongoUpdateService:       MongoUpdateService[Future]       = wire[HtsMongoUpdateService[Future]]

  lazy val bonusPeriodMilestonesService: BonusPeriodMilestonesService[Future] =
    wire[HtsBonusPeriodMilestonesService[Future]]

  lazy val bonusReachedMilestonesService: BonusReachedMilestonesService[Future] =
    wire[HtsBonusReachedMilestonesService[Future]]

  lazy val mongo:               MongoComponent            = MongoComponent(context.initialConfiguration.underlying.getString("mongodb.uri"))
  lazy val eligibilityRepo:     EligibilityRepo[Future]   = wire[MongoEligibilityRepo]
  lazy val eventRepo:           MongoSavingsGoalEventRepo = wire[MongoSavingsGoalEventRepo]
  lazy val previousBalanceRepo: MongoPreviousBalanceRepo  = wire[MongoPreviousBalanceRepo]
  lazy val milestonesRepo:      MongoMilestonesRepo       = wire[MongoMilestonesRepo]

  lazy val startupController:       StartupController       = wire[StartupController]
  lazy val helpToSaveController:    HelpToSaveController    = wire[HelpToSaveController]
  lazy val milestonesController:    MilestonesController    = wire[MilestonesController]
  lazy val documentationController: DocumentationController = wire[DocumentationController]
  lazy val metricsController:       MetricsController       = wire[MetricsController]
  lazy val sandboxController:       SandboxController       = wire[SandboxController]
  lazy val testController:          TestController          = wire[TestController]

  lazy val healthController: HealthController = wire[HealthController]

}
