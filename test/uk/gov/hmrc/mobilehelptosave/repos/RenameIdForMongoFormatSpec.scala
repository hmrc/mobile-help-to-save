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

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{Format, Json}

private case class TestCaseClass(scalaId: String, a: String, b: Int)

class RenameIdForMongoFormatSpec extends WordSpec with Matchers {

  "the Format returned" should {
    val format: Format[TestCaseClass] = RenameIdForMongoFormat("scalaId", Json.format[TestCaseClass])

    "rename _id to scalaId when reading whilst leaving other fields unchanged" in {
      val json = Json.obj(
        "_id" -> "test-internal-auth-id",
        "a" -> "foo",
        "b" -> 42
      )

      val parsedCaseClassInstance = json.as[TestCaseClass](format)

      parsedCaseClassInstance shouldBe TestCaseClass(
        "test-internal-auth-id",
        "foo",
        42
      )
    }

    "rename scalaId to _id when writing whilst leaving other fields unchanged" in {
      val caseClassInstance = TestCaseClass(
        "test-internal-auth-id",
        "foo",
        42
      )

      Json.toJson(caseClassInstance)(format) shouldBe Json.obj(
        "_id" -> "test-internal-auth-id",
        "a" -> "foo",
        "b" -> 42
      )
    }
  }

}
