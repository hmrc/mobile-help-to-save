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

import play.api.libs.json.{Format, JsPath, JsValue}

object RenameIdForMongoFormat {
  private def copyKey(fromPath: JsPath, toPath: JsPath) =
    JsPath.json.update(toPath.json.copyFrom(fromPath.json.pick))

  private def moveKey(fromPath: JsPath, toPath: JsPath) =
    (json: JsValue) => json.transform(copyKey(fromPath, toPath) andThen fromPath.json.prune).get

  /**
    * Returns a JSON format that renames a given field to _id (the name MongoDB / ReactiveMongo uses for IDs).
    */
  def apply[T](caseClassIdField: String, defaultFormat: Format[T]): Format[T] = {
    val caseClassIdPath: JsPath = JsPath \ caseClassIdField
    val mongoIdPath: JsPath = JsPath \ "_id"

    val mongoReads = defaultFormat.compose(JsPath.json.update(caseClassIdPath.json.copyFrom(mongoIdPath.json.pick)))
    val mongoWrites = defaultFormat.transform(moveKey(caseClassIdPath, mongoIdPath))

    Format(mongoReads, mongoWrites)
  }
}
