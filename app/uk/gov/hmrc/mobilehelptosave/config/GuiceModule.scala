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

import com.google.inject.AbstractModule
import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.api.connector.ServiceLocatorConnector
import uk.gov.hmrc.api.controllers.DocumentationController
import uk.gov.hmrc.http.{CoreGet, CorePost}
import uk.gov.hmrc.mobilehelptosave.api.{ApiServiceLocatorConnector, ServiceLocatorRegistrationTask}
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

class GuiceModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[CoreGet]).to(classOf[DefaultHttpClient])
    bind(classOf[CorePost]).to(classOf[DefaultHttpClient])
    bind(classOf[LoggerLike]).toInstance(Logger)

    bind(classOf[ServiceLocatorConnector]).to(classOf[ApiServiceLocatorConnector])
    bind(classOf[DocumentationController]).toInstance(DocumentationController)
    bind(classOf[ServiceLocatorRegistrationTask]).asEagerSingleton()
  }
}
