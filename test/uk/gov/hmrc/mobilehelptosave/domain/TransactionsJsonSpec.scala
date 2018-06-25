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

package uk.gov.hmrc.mobilehelptosave.domain

import com.eclipsesource.schema.SchemaType
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.Json
import uk.gov.hmrc.mobilehelptosave.TransactionTestData
import uk.gov.hmrc.mobilehelptosave.json.JsonResource.loadResourceJson
import uk.gov.hmrc.mobilehelptosave.json.Schema.banAdditionalProperties
import uk.gov.hmrc.mobilehelptosave.scalatest.SchemaMatchers

class TransactionsJsonSpec extends WordSpec with Matchers with SchemaMatchers with TransactionTestData {

  private val ramlTransactionsSchema =
    banAdditionalProperties(loadResourceJson("/public/api/conf/1.0/schemas/transactions.json"))
      .as[SchemaType]

  "Transactions JSON" when {
    "there is a typical list of transactions" should {
      "be a valid instance of the schema used in the RAML" in {
        Json.toJson(transactions) should validateAgainstSchema(ramlTransactionsSchema)
      }
    }

    "there are no transactions" should {
      "be a valid instance of the schema used in the RAML" in {
        val emptyTransactionsList = Transactions(transactions = Seq.empty)
        Json.toJson(emptyTransactionsList) should validateAgainstSchema(ramlTransactionsSchema)
      }
    }
  }
}
