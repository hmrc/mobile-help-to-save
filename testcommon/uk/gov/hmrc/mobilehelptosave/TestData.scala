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

package uk.gov.hmrc.mobilehelptosave

import java.time.LocalDate

import uk.gov.hmrc.mobilehelptosave.domain.{Credit, Debit, Transaction, Transactions}

trait TestData {

  val transactionsJsonString: String =
    """
      |{
      |  "transactions" : [ {
      |    "operation" : "credit",
      |    "amount" : 11.5,
      |    "transactionDate" : "2017-11-20",
      |    "accountingDate" : "2017-11-20",
      |    "description" : "Debit card online deposit",
      |    "transactionReference" : "A1A11AA1A00A0034",
      |    "balanceAfter" : 11.5
      |  }, {
      |    "operation" : "debit",
      |    "amount" : 1.01,
      |    "transactionDate" : "2017-11-27",
      |    "accountingDate" : "2017-11-27",
      |    "description" : "BACS payment",
      |    "transactionReference" : "A1A11AA1A00A000I",
      |    "balanceAfter" : 10.49
      |  }, {
      |    "operation" : "debit",
      |    "amount" : 1.11,
      |    "transactionDate" : "2017-11-27",
      |    "accountingDate" : "2017-11-27",
      |    "description" : "BACS payment",
      |    "transactionReference" : "A1A11AA1A00A000G",
      |    "balanceAfter" : 9.38
      |  }, {
      |    "operation" : "credit",
      |    "amount" : 1.11,
      |    "transactionDate" : "2017-11-27",
      |    "accountingDate" : "2017-12-04",
      |    "description" : "Reinstatement Adjustment",
      |    "transactionReference" : "A1A11AA1A00A000G",
      |    "balanceAfter" : 10.49
      |  }, {
      |    "operation" : "credit",
      |    "amount" : 50,
      |    "transactionDate" : "2018-04-10",
      |    "accountingDate" : "2018-04-10",
      |    "description" : "Debit card online deposit",
      |    "transactionReference" : "A1A11AA1A00A0059",
      |    "balanceAfter" : 60.49
      |  } ]
      |}""".stripMargin

  val transactions: Transactions = Transactions(Seq(
    Transaction(Credit, BigDecimal("11.50"), LocalDate.parse("2017-11-20"), LocalDate.parse("2017-11-20"), BigDecimal("11.50")),
    Transaction(Debit, BigDecimal("1.01"), LocalDate.parse("2017-11-27"), LocalDate.parse("2017-11-27"), BigDecimal("10.49")),
    Transaction(Debit, BigDecimal("1.11"), LocalDate.parse("2017-11-27"), LocalDate.parse("2017-11-27"), BigDecimal("9.38")),
    Transaction(Credit, BigDecimal("1.11"), LocalDate.parse("2017-11-27"), LocalDate.parse("2017-12-04"), BigDecimal("10.49")),
    Transaction(Credit, BigDecimal(50), LocalDate.parse("2018-04-10"), LocalDate.parse("2018-04-10"), BigDecimal("60.49"))
  ))
}
