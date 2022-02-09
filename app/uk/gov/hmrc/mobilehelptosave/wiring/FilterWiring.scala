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
import com.kenshoo.play.metrics.{Metrics, MetricsFilterImpl, MetricsImpl}
import com.softwaremill.macwire.wire
import play.api.BuiltInComponents
import play.api.mvc.EssentialFilter
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.config.AuditingConfig
import uk.gov.hmrc.play.audit.http.connector.{AuditChannel, AuditConnector, Counter, DatastreamMetrics}
import uk.gov.hmrc.play.bootstrap.audit.{DefaultAuditChannel, DefaultAuditConnector, EnabledDatastreamMetricsProvider}
import uk.gov.hmrc.play.bootstrap.config.{AppName, AuditingConfigProvider, ControllerConfigs, DefaultHttpAuditEvent, HttpAuditEvent}
import uk.gov.hmrc.play.bootstrap.filters._
import uk.gov.hmrc.play.bootstrap.backend.filters.{BackendAuditFilter, BackendFilters, DefaultBackendAuditFilter}
import uk.gov.hmrc.play.bootstrap.graphite.GraphiteReporterProviderConfig
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpAuditing

trait FilterWiring {
  self: BuiltInComponents =>

  private lazy val appName: String = AppName.fromConfiguration(configuration)

  lazy val metrics: Metrics = wire[MetricsImpl]

  lazy val controllerConfigs:  ControllerConfigs  = ControllerConfigs.fromConfig(configuration)
  lazy val cacheControlConfig: CacheControlConfig = CacheControlConfig.fromConfig(configuration)

  lazy val graphiteConfig: GraphiteReporterProviderConfig =
    GraphiteReporterProviderConfig.fromConfig(configuration, configuration)

  lazy val httpAuditEvent: HttpAuditEvent = wire[DefaultHttpAuditEvent]

  lazy val metricsFilter:           MetricsFilterImpl  = wire[MetricsFilterImpl]
  lazy val microserviceAuditFilter: BackendAuditFilter = wire[DefaultBackendAuditFilter]
  lazy val loggingFilter:           LoggingFilter      = wire[DefaultLoggingFilter]
  lazy val cacheControlFilter:      CacheControlFilter = wire[CacheControlFilter]
  lazy val MDCFilter:               MDCFilter          = wire[MDCFilter]
  lazy val backendFilters:          BackendFilters     = wire[BackendFilters]

  lazy val auditConnector:                 AuditConnector                 = wire[DefaultAuditConnector]
  lazy val httpAuditing:                   HttpAuditing                   = wire[DefaultHttpAuditing]
  lazy val auditChannel:                   AuditChannel                   = wire[DefaultAuditChannel]
  lazy val datastreamMetrics:              DatastreamMetrics              = wire[EnabledDatastreamMetricsProvider].get()

  lazy val httpAuditingConfig: AuditingConfig = wire[AuditingConfigProvider].get

  override def httpFilters: Seq[EssentialFilter] = backendFilters.filters
}