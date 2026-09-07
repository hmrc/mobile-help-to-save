/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.mobilehelptosave.repository

import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.crypto.{OnewayCryptoFactory, PlainText, Sha512Crypto}
import uk.gov.hmrc.mobilehelptosave.config.EncryptionConfig

class NinoHash(config: EncryptionConfig) {
  private val hasher: Sha512Crypto = OnewayCryptoFactory.sha(config.encryptionHashKey)

  def apply(nino: Nino): String = hasher.hash(PlainText(nino.nino)).value
}
