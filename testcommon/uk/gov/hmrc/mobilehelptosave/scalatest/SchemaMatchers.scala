/*
 * Copyright 2022 HM Revenue & Customs
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

import com.eclipsesource.schema.{SchemaType, SchemaValidator}
import org.scalatest.matchers.{MatchResult, Matcher}
import play.api.libs.json.{JsError, JsSuccess, JsValue}

trait SchemaMatchers {

  protected lazy val schemaValidator: SchemaValidator = SchemaValidator()

  def validateAgainstSchema(schema: SchemaType): Matcher[JsValue] = new Matcher[JsValue] {

    override def toString(): String = """validateAgainstSchema (<schema>)"""

    override def apply(left: JsValue): MatchResult =
      schemaValidator.validate(schema, left) match {
        case JsSuccess(_, _) =>
          MatchResult(
            matches = true,
            "JSON was not valid against schema",
            "JSON was valid against schema"
          )
        case error @ JsError(_) =>
          MatchResult(
            matches = false,
            "JSON was not valid against schema, errors: {0}",
            "JSON was valid against schema",
            IndexedSeq(error.errors)
          )
      }
  }

}

/**
  * Companion object that facilitates the importing of <code>SchemaMatchers</code> members as
  * an alternative to mixing it the trait. One use case is to import <code>SchemaMatchers</code> members so you can use
  * them in the Scala interpreter.
  */
object SchemaMatchers extends SchemaMatchers
