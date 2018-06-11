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

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.Mode.Mode
import uk.gov.hmrc.play.config.ServicesConfig

@Singleton
case class MobileHelpToSaveConfig @Inject()(configuration: Configuration, override val mode:Mode)
  extends ServicesConfig
    with HelpToSaveConnectorConfig {

  override protected def runModeConfiguration: Configuration = configuration

  protected def baseUrlAsUrl(serviceName: String): URL = new URL(baseUrl(serviceName))

  lazy val helpToSaveBaseUrl: URL = baseUrlAsUrl("help-to-save")
  lazy val helpToSaveProxyBaseUrl: URL = baseUrlAsUrl("help-to-save-proxy")
}

@ImplementedBy(classOf[MobileHelpToSaveConfig])
trait HelpToSaveConnectorConfig {
  def helpToSaveBaseUrl: URL
}
