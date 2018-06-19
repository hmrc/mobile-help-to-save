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
import play.api.Mode.Mode
import play.api.{Configuration, Environment}
import uk.gov.hmrc.mobilehelptosave.config.{Base64, EnabledInvitationFilters}
import uk.gov.hmrc.mobilehelptosave.domain.Shuttering
import uk.gov.hmrc.play.config.ServicesConfig

@Singleton
case class MobileHelpToSaveConfig @Inject()(
  environment: Environment,
  configuration: Configuration
)
  extends ServicesConfig
    with EnabledInvitationFilters
    with HelpToSaveConnectorConfig
    with HelpToSaveProxyConnectorConfig
    with NativeAppWidgetConnectorConfig
    with NinoWithoutWtcMongoRepositoryConfig
    with StartupControllerConfig
    with TaxCreditsBrokerConnectorConfig
    with TransactionControllerConfig
    with UserServiceConfig {

  override protected lazy val mode: Mode = environment.mode
  override protected def runModeConfiguration: Configuration = configuration

  // These are eager vals so that missing or invalid configuration will be detected on startup
  override val helpToSaveBaseUrl: URL = configBaseUrl("help-to-save")
  override val helpToSaveProxyBaseUrl: URL = configBaseUrl("help-to-save-proxy")
  override val nativeAppWidgetBaseUrl: URL = configBaseUrl("native-app-widget")
  override val taxCreditsBrokerBaseUrl: URL = configBaseUrl("tax-credits-broker")

  override val shuttering: Shuttering = Shuttering(
    shuttered = configBoolean("helpToSave.shuttering.shuttered"),
    title = configBase64String("helpToSave.shuttering.title"),
    message = configBase64String("helpToSave.shuttering.message")
  )

  override val helpToSaveEnabled: Boolean = configBoolean("helpToSave.enabled")
  override val balanceEnabled: Boolean = configBoolean("helpToSave.balanceEnabled")
  override val paidInThisMonthEnabled: Boolean = configBoolean("helpToSave.paidInThisMonthEnabled")
  override val firstBonusEnabled: Boolean = configBoolean("helpToSave.firstBonusEnabled")
  override val shareInvitationEnabled: Boolean = configBoolean("helpToSave.shareInvitationEnabled")
  override val savingRemindersEnabled: Boolean = configBoolean("helpToSave.savingRemindersEnabled")
  override val transactionsEnabled: Boolean = configBoolean("helpToSave.transactionsEnabled")
  override val helpToSaveInfoUrl: String = configString("helpToSave.infoUrl")
  override val helpToSaveInvitationUrl: String = configString("helpToSave.invitationUrl")
  override val helpToSaveAccessAccountUrl: String = configString("helpToSave.accessAccountUrl")
  override val dailyInvitationCap: Int = configInt("helpToSave.dailyInvitationCap")
  override val taxCreditsCacheExpireAfterSeconds: Long = configLong("helpToSave.taxCreditsCache.expireAfterSeconds")

  override val surveyInvitationFilter: Boolean = configBoolean("helpToSave.invitationFilters.survey")
  override val workingTaxCreditsInvitationFilter: Boolean = configBoolean("helpToSave.invitationFilters.workingTaxCredits")

  protected def configBaseUrl(serviceName: String): URL = new URL(baseUrl(serviceName))

  private def configBoolean(path: String): Boolean = configuration.underlying.getBoolean(path)

  private def configInt(path: String): Int = configuration.underlying.getInt(path)

  private def configLong(path: String): Long = configuration.underlying.getLong(path)

  private def configString(path: String): String = configuration.underlying.getString(path)

  private def configBase64String(path: String): String = {
    val encoded = configuration.underlying.getString(path)
    Base64.decode(encoded)
  }
}

@ImplementedBy(classOf[MobileHelpToSaveConfig])
trait HelpToSaveConnectorConfig {
  def helpToSaveBaseUrl: URL
}

@ImplementedBy(classOf[MobileHelpToSaveConfig])
trait HelpToSaveProxyConnectorConfig {
  def helpToSaveProxyBaseUrl: URL
}

@ImplementedBy(classOf[MobileHelpToSaveConfig])
trait NativeAppWidgetConnectorConfig {
  def nativeAppWidgetBaseUrl: URL
}

@ImplementedBy(classOf[MobileHelpToSaveConfig])
trait NinoWithoutWtcMongoRepositoryConfig {
  def taxCreditsCacheExpireAfterSeconds: Long
}

@ImplementedBy(classOf[MobileHelpToSaveConfig])
trait StartupControllerConfig {
  def shuttering: Shuttering
  def helpToSaveEnabled: Boolean
  def balanceEnabled: Boolean
  def paidInThisMonthEnabled: Boolean
  def firstBonusEnabled: Boolean
  def shareInvitationEnabled: Boolean
  def savingRemindersEnabled: Boolean
  def transactionsEnabled: Boolean
  def helpToSaveInfoUrl: String
  def helpToSaveInvitationUrl: String
  def helpToSaveAccessAccountUrl: String  
}

@ImplementedBy(classOf[MobileHelpToSaveConfig])
trait TaxCreditsBrokerConnectorConfig {
  def taxCreditsBrokerBaseUrl: URL
}

@ImplementedBy(classOf[MobileHelpToSaveConfig])
trait TransactionControllerConfig {
  def shuttering: Shuttering
}

@ImplementedBy(classOf[MobileHelpToSaveConfig])
trait UserServiceConfig {
  def helpToSaveEnabled: Boolean
  def dailyInvitationCap: Int
  def balanceEnabled: Boolean
  def paidInThisMonthEnabled: Boolean
  def firstBonusEnabled: Boolean
}
