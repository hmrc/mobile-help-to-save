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

package uk.gov.hmrc.mobilehelptosave.scalatest

import com.eclipsesource.schema.drafts.Version7._
import com.eclipsesource.schema.SchemaType
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.mobilehelptosave.io.Resources
import uk.gov.hmrc.mobilehelptosave.scalatest.SchemaMatchers.validateAgainstSchema

class SchemaMatchersSpec extends AnyWordSpecLike with Matchers {
  private val accountSchema: SchemaType = loadResourceJson("test_schema.json").as[SchemaType]
  val goodJsonExampleResourceName = "valid_instance.json"
  val badJsonExampleResourceName  = "invalid_instance.json"

  "validateAgainstSchema(resourceName) method returns Matcher" should {
    val matcher = validateAgainstSchema(accountSchema)

    "have pretty toString" in {
      matcher.toString shouldBe "validateAgainstSchema (<schema>)"
    }

    "have correct MatchResult when JSON matches schema" in {
      val goodJson: JsValue = loadResourceJson(goodJsonExampleResourceName)
      val goodJsonResult = matcher(goodJson)

      goodJsonResult.matches               shouldBe true
      goodJsonResult.failureMessage        shouldBe "JSON was not valid against schema"
      goodJsonResult.negatedFailureMessage shouldBe "JSON was valid against schema"
    }

    "have correct MatchResult when JSON does not matche schema" in {
      val badJson: JsValue = loadResourceJson(badJsonExampleResourceName)
      val badJsonResult = matcher(badJson)

      badJsonResult.matches shouldBe false
      badJsonResult.failureMessage should (startWith("JSON was not valid against schema")
      and include("Property line1 missing")
      and include("Property line3 missing"))

      badJsonResult.negatedFailureMessage shouldBe "JSON was valid against schema"
    }
  }

  private def loadResourceJson(resourceName: String): JsValue =
    Resources.withResource(resourceName, getClass)(Json.parse)

}
