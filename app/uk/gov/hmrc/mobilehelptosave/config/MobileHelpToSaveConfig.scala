/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.mobilehelptosave.config

import java.net.URL

import play.api.{Configuration, Environment}
import uk.gov.hmrc.mobilehelptosave.domain.Shuttering
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.collection.JavaConverters._

case class MobileHelpToSaveConfig(
  environment:    Environment,
  configuration:  Configuration,
  servicesConfig: ServicesConfig
) extends AccountServiceConfig
    with DocumentationControllerConfig
    with HelpToSaveConnectorConfig
    with HelpToSaveControllerConfig
    with MilestonesControllerConfig
    with SandboxDataConfig
    with StartupControllerConfig {

  // These are eager vals so that missing or invalid configuration will be detected on startup
  override val helpToSaveBaseUrl: URL = configBaseUrl("help-to-save")

  override val shuttering: Shuttering = Shuttering(
    shuttered = configBoolean("helpToSave.shuttering.shuttered"),
    title     = configBase64String("helpToSave.shuttering.title"),
    message   = configBase64String("helpToSave.shuttering.message")
  )

  override def savingsGoalsEnabled: Boolean = configBoolean("helpToSave.savingsGoalsEnabled")

  override val inAppPaymentsEnabled:       Boolean = configBoolean("helpToSave.inAppPaymentsEnabled")
  override val helpToSaveInfoUrl:          String  = configString("helpToSave.infoUrl")
  override val helpToSaveInfoUrlSso:       String  = configString("helpToSave.infoUrlSso")
  override val helpToSaveAccessAccountUrl: String  = configString("helpToSave.accessAccountUrl")
  override val helpToSaveAccountPayInUrl:  String  = configString("helpToSave.accountPayInUrl")

  private val accessConfig = configuration.underlying.getConfig("api.access")
  override val apiAccessType:              String      = accessConfig.getString("type")
  override val apiWhiteListApplicationIds: Seq[String] = accessConfig.getStringList("white-list.applicationIds").asScala

  protected def configBaseUrl(serviceName: String): URL = new URL(servicesConfig.baseUrl(serviceName))

  private def configBoolean(path: String): Boolean = configuration.underlying.getBoolean(path)

  private def configString(path: String): String = configuration.underlying.getString(path)
  private def configDouble(path: String): Double = configuration.underlying.getDouble(path)

  private def configBase64String(path: String): String = {
    val encoded = configuration.underlying.getString(path)
    Base64.decode(encoded)
  }
}

trait AccountServiceConfig {
  def inAppPaymentsEnabled: Boolean
  def savingsGoalsEnabled:  Boolean
}

trait SandboxDataConfig {
  def inAppPaymentsEnabled: Boolean
}

trait DocumentationControllerConfig {
  def apiAccessType:              String
  def apiWhiteListApplicationIds: Seq[String]
}

trait HelpToSaveConnectorConfig {
  def helpToSaveBaseUrl: URL
}

trait StartupControllerConfig {
  def shuttering:                 Shuttering
  def helpToSaveInfoUrl:          String
  def helpToSaveInfoUrlSso:       String
  def helpToSaveAccessAccountUrl: String
  def helpToSaveAccountPayInUrl:  String
}

trait HelpToSaveControllerConfig {
  def shuttering: Shuttering
}

trait MilestonesControllerConfig {
  def shuttering: Shuttering
}
