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

package uk.gov.hmrc.mobilehelptosave.config

import uk.gov.hmrc.mobilehelptosave.support.BaseSpec

class Base64Spec extends BaseSpec {
  "decode" should {
    "decode simple values" in {
      Base64.decode("YXNkZg==") shouldBe "asdf"
    }

    "decode non-ASCII characters using UTF-8 encoding" in {
      Base64.decode("4oCcc21hcnQgcXVvdGVz4oCd") shouldBe "\u201csmart quotes\u201d"
    }

    "decode empty value to empty string" in {
      Base64.decode("") shouldBe ""
    }
  }
}
