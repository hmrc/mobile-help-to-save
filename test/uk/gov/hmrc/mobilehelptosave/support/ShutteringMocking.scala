/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.mobilehelptosave.support

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.connectors.{HttpClientV2Helper, ShutteringConnector}
import uk.gov.hmrc.mobilehelptosave.domain.Shuttering
import scala.concurrent.{ExecutionContext, Future}


trait ShutteringMocking extends HttpClientV2Helper{

  val trueShuttering      = Shuttering(shuttered = true, Some("Shuttered"), Some("HTS is currently not available"))
  val falseShuttering     = Shuttering.shutteringDisabled
  val shutteringConnector = mock[ShutteringConnector]


  def shutteringEnabled: Unit = {
    when(shutteringConnector.getShutteringStatus(any[String])(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Future.successful(trueShuttering))
  }

  def shutteringDisabled: Unit = {
    when(shutteringConnector.getShutteringStatus(any[String])(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Future.successful(falseShuttering))
  }
}
