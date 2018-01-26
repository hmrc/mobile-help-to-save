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

package uk.gov.hmrc.mobilehelptosave.metrics

import com.codahale.metrics.{Counter, MetricRegistry}
import com.kenshoo.play.metrics.Metrics

object FakeMobileHelpToSaveMetrics {
  def apply(): MobileHelpToSaveMetrics = new MobileHelpToSaveMetrics(new FakeMetrics())

}

object ShouldNotUpdateInvitationMetrics extends MobileHelpToSaveMetrics(new FakeMetrics()) {
  override def counter(name: String): Counter = new ShouldNotUpdateCounter(name)
}

class ShouldNotUpdateCounter(name: String) extends Counter {
  private def shouldNotBeUpdated(): Nothing = throw new RuntimeException(s"The $name counter should not be updated in this situation")

  override def dec(): Unit = shouldNotBeUpdated()

  override def dec(n: Long): Unit = shouldNotBeUpdated()

  override def inc(): Unit = shouldNotBeUpdated()

  override def inc(n: Long): Unit = shouldNotBeUpdated()
}

class FakeMetrics extends Metrics {
  override val defaultRegistry: MetricRegistry = new MetricRegistry

  override def toJson: String = throw new NotImplementedError
}
