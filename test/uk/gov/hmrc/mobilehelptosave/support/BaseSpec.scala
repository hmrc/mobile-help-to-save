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

package uk.gov.hmrc.mobilehelptosave.support

import org.scalatest.Outcome
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

trait BaseSpec extends PlaySpec with MockitoSugar {

  val generator = new Generator(0)
  val nino:           Nino         = generator.nextNino
  val otherNino:      Nino         = generator.nextNino
  val mockHttpClient: HttpClientV2 = mock[HttpClientV2]
}
