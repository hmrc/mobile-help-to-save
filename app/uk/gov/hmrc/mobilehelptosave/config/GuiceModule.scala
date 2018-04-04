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

package uk.gov.hmrc.mobilehelptosave.config

import java.net.URL

import com.google.inject.AbstractModule
import com.google.inject.name.Names.named
import javax.inject.Provider
import play.api.Mode.Mode
import play.api.{Configuration, Environment, Logger, LoggerLike}
import uk.gov.hmrc.http.CoreGet
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import uk.gov.hmrc.play.config.ServicesConfig

class GuiceModule(environment: Environment, configuration: Configuration) extends AbstractModule with ServicesConfig {

  override protected lazy val mode: Mode = environment.mode
  override protected lazy val runModeConfiguration: Configuration = configuration

  override def configure(): Unit = {
    bindConfigBoolean("helpToSave.shuttering.shuttered")
    bindConfigBoolean("helpToSave.enabled")
    bindConfigBoolean("helpToSave.balanceEnabled")
    bindConfigBoolean("helpToSave.paidInThisMonthEnabled")
    bindConfigBoolean("helpToSave.firstBonusEnabled")
    bindConfigBoolean("helpToSave.shareInvitationEnabled")
    bindConfigBoolean("helpToSave.savingRemindersEnabled")
    bindConfigString("helpToSave.infoUrl")
    bindConfigString("helpToSave.invitationUrl")
    bindConfigString("helpToSave.accessAccountUrl")
    bindConfigInt("helpToSave.dailyInvitationCap")
    bindConfigBoolean("helpToSave.invitationFilters.survey")
    bindConfigBoolean("helpToSave.invitationFilters.workingTaxCredits")

    bindBaseUrl("help-to-save")
    bindBaseUrl("help-to-save-proxy")
    bindBaseUrl("native-app-widget")
    bindBaseUrl("tax-credits-broker")

    bind(classOf[CoreGet]).to(classOf[DefaultHttpClient])
    bind(classOf[LoggerLike]).toInstance(Logger)
  }

  private def bindConfigBoolean(path: String): Unit = {
    bindConstant().annotatedWith(named(path)).to(configuration.underlying.getBoolean(path))
  }

  private def bindConfigInt(path: String): Unit = {
    bindConstant().annotatedWith(named(path)).to(configuration.underlying.getInt(path))
  }

  private def bindConfigString(path: String): Unit = {
    bindConstant().annotatedWith(named(path)).to(configuration.underlying.getString(path))
  }

  private def bindBaseUrl(serviceName: String) =
    bind(classOf[URL]).annotatedWith(named(s"$serviceName-baseUrl")).toProvider(new BaseUrlProvider(serviceName))

  private class BaseUrlProvider(serviceName: String) extends Provider[URL] {
    override lazy val get = new URL(baseUrl(serviceName))
  }

}
