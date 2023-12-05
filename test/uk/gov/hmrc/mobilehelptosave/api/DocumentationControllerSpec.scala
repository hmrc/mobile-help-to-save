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

package uk.gov.hmrc.mobilehelptosave.api

import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.http.HttpErrorHandler
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import uk.gov.hmrc.mobilehelptosave.config.DocumentationControllerConfig

import scala.collection.mutable

class DocumentationControllerSpec
    extends AnyWordSpecLike
    with Matchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with MockFactory {
  "definition" should {
    "have content type = application/json" in {
      val controller = new DocumentationController(
        mock[HttpErrorHandler],
        TestDocumentationControllerConfig,
        stubControllerComponents(),
        null // nasty, but there's not apparent way to create a test `Assets` and the test does not use it, so...
      )
      val result: Result = await(controller.definition()(FakeRequest()))
      result.body.contentType shouldBe Some("application/json;charset=utf-8")
    }
  }
}

private object TestDocumentationControllerConfig extends DocumentationControllerConfig {
  override def apiAccessType              = "PRIVATE"
  override def apiWhiteListApplicationIds = mutable.Buffer.empty[String]
}
