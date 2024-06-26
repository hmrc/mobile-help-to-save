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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Json
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.ExecutionContext.Implicits.global

class FakeHttpGetSpec extends AnyWordSpecLike with Matchers with FutureAwaits with DefaultAwaitTimeout {

  "doGet" should {
    "return the specified response when the URL matches expectedUrl" in {
      val response = HttpResponse(200, Json.obj("this" -> "that"), Map.empty)

      await(FakeHttpGet("http://example.com/expected", response).doGet("http://example.com/expected")) shouldBe response
    }

    "return 404 for other URLs" in {
      val response = HttpResponse(200, Json.obj("this" -> "that"), Map.empty)

      await(FakeHttpGet("http://example.com/expected", response).doGet("http://example.com/not-expected")).status shouldBe 404
    }

    "return the specified response when the URL predicate returns true" in {
      val response = HttpResponse(200, Json.obj("this" -> "that"), Map.empty)

      await(FakeHttpGet((_: String) => true, response).doGet("http://example.com/expected")) shouldBe response
    }

    "return 404 for other when the URL predicate returns false" in {
      val response = HttpResponse(200, Json.obj("this" -> "that"), Map.empty)

      await(FakeHttpGet((_: String) => false, response).doGet("http://example.com/not-expected")).status shouldBe 404
    }
  }

}
