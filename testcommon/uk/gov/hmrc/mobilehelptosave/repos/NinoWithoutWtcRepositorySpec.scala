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

package uk.gov.hmrc.mobilehelptosave.repos

import org.joda.time.{DateTime, DateTimeZone}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.mobilehelptosave.domain.NinoWithoutWtc

trait NinoWithoutWtcRepositorySpec extends RepositorySpec[NinoWithoutWtc, Nino] {

  override val repo: NinoWithoutWtcRepository

  private val generator = new Generator(0)

  override def createId(): Nino = generator.nextNino
  override def createOtherId(): Nino = generator.nextNino

  override def createEntity(id: Nino): NinoWithoutWtc = NinoWithoutWtc(id, DateTime.now(DateTimeZone.UTC))
  override def createOtherEntity(otherId: Nino): NinoWithoutWtc = NinoWithoutWtc(otherId, DateTime.now(DateTimeZone.UTC).minusDays(1))
}
