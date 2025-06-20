/*
 * Copyright 2023 HM Revenue & Customs
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
import com.softwaremill.macwire.wire
import play.api.BuiltInComponents
import play.api.http.NoHttpFilters.filters
import play.api.mvc.EssentialFilter
import uk.gov.hmrc.play.audit.{DefaultAuditChannel, DefaultAuditConnector}
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.config.AuditingConfig
import uk.gov.hmrc.play.audit.http.connector.{AuditChannel, AuditConnector, DatastreamMetrics}
import uk.gov.hmrc.play.bootstrap.audit.EnabledDatastreamMetricsProvider
import uk.gov.hmrc.play.bootstrap.config.{AppName, ControllerConfigs, DefaultHttpAuditEvent, HttpAuditEvent}
import uk.gov.hmrc.play.bootstrap.filters.*
import uk.gov.hmrc.play.bootstrap.backend.filters.{BackendAuditFilter, BackendMdcFilter, DefaultBackendAuditFilter}
import uk.gov.hmrc.play.bootstrap.graphite.GraphiteReporterProviderConfig
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpAuditing
import uk.gov.hmrc.play.bootstrap.metrics.{Metrics, MetricsFilterImpl, MetricsImpl}
import play.api.http.{DefaultHttpFilters, EnabledFilters}

trait FilterWiring {
  self: BuiltInComponents =>

  private lazy val appName: String = AppName.fromConfiguration(configuration)

  lazy val metrics: Metrics = wire[MetricsImpl]

  lazy val controllerConfigs: ControllerConfigs = ControllerConfigs.fromConfig(configuration)
  lazy val cacheControlConfig: CacheControlConfig = CacheControlConfig.fromConfig(configuration)

  lazy val graphiteConfig: GraphiteReporterProviderConfig =
    GraphiteReporterProviderConfig.fromConfig(configuration)

  lazy val httpAuditEvent: HttpAuditEvent = wire[DefaultHttpAuditEvent]

  lazy val metricsFilter: MetricsFilterImpl = wire[MetricsFilterImpl]
  lazy val microserviceAuditFilter: BackendAuditFilter = wire[DefaultBackendAuditFilter]
  lazy val loggingFilter: LoggingFilter = wire[DefaultLoggingFilter]
  lazy val cacheControlFilter: CacheControlFilter = wire[CacheControlFilter]
  lazy val MDCFilter: MDCFilter = wire[BackendMdcFilter]

  lazy val auditConnector: AuditConnector = wire[DefaultAuditConnector]
  lazy val httpAuditing: HttpAuditing = wire[DefaultHttpAuditing]
  lazy val auditChannel: AuditChannel = wire[DefaultAuditChannel]
  lazy val datastreamMetrics: DatastreamMetrics = wire[EnabledDatastreamMetricsProvider].get()

  lazy val httpAuditingConfig: AuditingConfig = AuditingConfig.fromConfig(configuration)

  override def httpFilters: Seq[EssentialFilter] = filters
}
