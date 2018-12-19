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

package uk.gov.hmrc.mobilehelptosave.wiring

import com.kenshoo.play.metrics.{Metrics, MetricsController, MetricsImpl}
import com.softwaremill.macwire.wire
import play.api.ApplicationLoader.Context
import play.api.http.{DefaultHttpFilters, HttpRequestHandler}
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.{BuiltInComponentsFromContext, Logger, LoggerLike}
import play.modules.reactivemongo.{ReactiveMongoComponent, ReactiveMongoComponentImpl}
import prod.Routes
import uk.gov.hmrc.api.connector.{ApiServiceLocatorConnector, ServiceLocatorConnector}
import uk.gov.hmrc.api.sandbox.RoutingHttpRequestHandler
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.mobilehelptosave.api.{DocumentationController, ServiceLocatorRegistrationTask}
import uk.gov.hmrc.mobilehelptosave.config.MobileHelpToSaveConfig
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveConnectorImpl
import uk.gov.hmrc.mobilehelptosave.controllers._
import uk.gov.hmrc.mobilehelptosave.repository.{MongoSavingsGoalEventRepo, SavingsGoalEventRepo}
import uk.gov.hmrc.mobilehelptosave.sandbox.SandboxData
import uk.gov.hmrc.mobilehelptosave.services._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.audit.DefaultAuditConnector
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

import scala.concurrent.ExecutionContext

class ServiceComponents(context: Context)
  extends BuiltInComponentsFromContext(context)
    with AhcWSComponents {

  implicit val ec: ExecutionContext = actorSystem.dispatcher

  lazy val prodLogger: LoggerLike = Logger

  override lazy val httpRequestHandler: HttpRequestHandler =
    new RoutingHttpRequestHandler(router, httpErrorHandler, httpConfiguration, new DefaultHttpFilters(httpFilters: _*), environment, configuration)

  lazy val prefix          : String            = "/"
  lazy val sandboxRouter   : sandbox.Routes    = wire[sandbox.Routes]
  lazy val definitionRouter: definition.Routes = wire[definition.Routes]
  lazy val healthRouter    : health.Routes     = wire[health.Routes]
  lazy val appRouter       : app.Routes        = wire[app.Routes]
  lazy val apiRouter       : api.Routes        = wire[api.Routes]

  lazy val router: Routes = wire[prod.Routes]

  lazy val ws: DefaultHttpClient = wire[DefaultHttpClient]

  lazy val clock      : Clock       = wire[ClockImpl]
  lazy val metrics    : Metrics     = wire[MetricsImpl]
  lazy val sandboxData: SandboxData = wire[SandboxData]

  lazy val authorisedWithIds: AuthorisedWithIds = wire[AuthorisedWithIdsImpl]

  lazy val helpToSaveConfig: MobileHelpToSaveConfig = wire[MobileHelpToSaveConfig]

  lazy val helpToSaveConnector    : HelpToSaveConnectorImpl = wire[HelpToSaveConnectorImpl]
  lazy val auditConnector         : AuditConnector          = wire[DefaultAuditConnector]
  lazy val authConnector          : AuthConnector           = wire[DefaultAuthConnector]
  lazy val serviceLocatorConnector: ServiceLocatorConnector = wire[ApiServiceLocatorConnector]

  lazy val userService   : UserService    = wire[UserService]
  lazy val accountService: AccountService = wire[HelpToSaveAccountService]

  lazy val mongo    : ReactiveMongoComponent = wire[ReactiveMongoComponentImpl]
  lazy val eventRepo: SavingsGoalEventRepo   = wire[MongoSavingsGoalEventRepo]

  lazy val startupController      : StartupController       = wire[StartupController]
  lazy val helpToSaveController   : HelpToSaveController    = wire[HelpToSaveController]
  lazy val documentationController: DocumentationController = wire[DocumentationController]
  lazy val metricsController      : MetricsController       = wire[MetricsController]
  lazy val sandboxController      : SandboxController       = wire[SandboxController]

  val registrationTask: ServiceLocatorRegistrationTask = wire[ServiceLocatorRegistrationTask]
}
