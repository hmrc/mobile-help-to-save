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

package uk.gov.hmrc.config

import java.net.URL

import javax.inject.{Inject, Provider}
import play.api.Mode.Mode
import play.api.{Configuration, Mode}
import uk.gov.hmrc.play.config.ServicesConfig
import java.net.URL
import com.google.inject.ImplementedBy

trait HelpToSaveServicesConfig extends ServicesConfig {

  val configuration: Configuration

  protected class BaseUrlProvider(serviceName: String) extends Provider[URL] {
    override lazy val get = new URL(baseUrl(serviceName))
  }
}


object HelpToSaveConfig {

  def from(configBase: Map[String, Any], mode: Mode = Mode.Dev): HelpToSaveConfig = {
    HelpToSaveConfig(Configuration.from(configBase), mode)
  }

  def empty: HelpToSaveConfig = from(Map.empty)
}

case class HelpToSaveConfig @Inject()(override val configuration: Configuration, override val mode:Mode)
  extends ServicesConfig
    with HelpToSaveConnectorConfig {

  override protected def runModeConfiguration: Configuration = configuration

  protected class BaseUrlProvider(serviceName: String) extends Provider[URL] {
    override lazy val get = new URL(baseUrl(serviceName))
  }
}

@ImplementedBy(classOf[HelpToSaveConfig])
trait HelpToSaveConnectorConfig extends HelpToSaveServicesConfig {

  lazy val connectorBaseUrl: URL =  new BaseUrlProvider("help-to-save").get
  lazy val proxyConnectorBaseUrl: URL =  new BaseUrlProvider("help-to-save-proxy").get
  lazy val enrolmentStatusUrl: URL = new URL(connectorBaseUrl, "/help-to-save/enrolment-status")
  def transactionsUrl(nino:String): URL = new URL(connectorBaseUrl, s"/help-to-save/$nino/account/transactions")
}