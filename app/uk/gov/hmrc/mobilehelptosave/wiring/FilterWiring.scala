/*
 * Copyright 2021 HM Revenue & Customs
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
import com.kenshoo.play.metrics.{Metrics, MetricsFilterImpl, MetricsImpl}
import com.softwaremill.macwire.wire
import play.api.BuiltInComponents
import play.api.mvc.EssentialFilter
import uk.gov.hmrc.play.bootstrap.config.{AppName, ControllerConfigs, DefaultHttpAuditEvent, HttpAuditEvent}
import uk.gov.hmrc.play.bootstrap.filters._
import uk.gov.hmrc.play.bootstrap.backend.filters.{BackendAuditFilter, DefaultBackendAuditFilter}

trait FilterWiring {
  self: BuiltInComponents with AuditWiring =>

  private lazy val appName: String = AppName.fromConfiguration(configuration)

  lazy val metrics: Metrics = wire[MetricsImpl]

  lazy val controllerConfigs:  ControllerConfigs  = ControllerConfigs.fromConfig(configuration)
  lazy val cacheControlConfig: CacheControlConfig = CacheControlConfig.fromConfig(configuration)

  lazy val httpAuditEvent: HttpAuditEvent = wire[DefaultHttpAuditEvent]

  lazy val metricsFilter:           MetricsFilterImpl  = wire[MetricsFilterImpl]
  lazy val microserviceAuditFilter: BackendAuditFilter = wire[DefaultBackendAuditFilter]
  lazy val loggingFilter:           LoggingFilter      = wire[DefaultLoggingFilter]
  lazy val cacheControlFilter:      CacheControlFilter = wire[CacheControlFilter]
  lazy val MDCFilter:               MDCFilter          = wire[MDCFilter]
  lazy val microserviceFilters:     MicroserviceFilters     = wire[MicroserviceFilters]

  override def httpFilters: Seq[EssentialFilter] = microserviceFilters.filters
}
