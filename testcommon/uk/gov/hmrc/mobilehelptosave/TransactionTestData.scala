/*
 * Copyright 2021 HM Revenue & Customs
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

trait TransactionTestData {

  protected val transactionsReturnedByHelpToSaveJsonString: String =
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

  protected val transactionsReturnedByMobileHelpToSaveJsonString: String =
    """
      |{
      |  "transactions" : [
      |    {
      |      "operation" : "credit",
      |      "amount" : 50,
      |      "transactionDate" : "2018-04-10",
      |      "accountingDate" : "2018-04-10",
      |      "balanceAfter" : 60.49
      |    }, {
      |      "operation" : "credit",
      |      "amount" : 1.11,
      |      "transactionDate" : "2017-11-27",
      |      "accountingDate" : "2017-12-04",
      |      "balanceAfter" : 10.49
      |    }, {
      |      "operation" : "debit",
      |      "amount" : 1.11,
      |      "transactionDate" : "2017-11-27",
      |      "accountingDate" : "2017-11-27",
      |      "balanceAfter" : 9.38
      |    }, {
      |      "operation" : "debit",
      |      "amount" : 1.01,
      |      "transactionDate" : "2017-11-27",
      |      "accountingDate" : "2017-11-27",
      |      "balanceAfter" : 10.49
      |    }, {
      |      "operation" : "credit",
      |      "amount" : 11.5,
      |      "transactionDate" : "2017-11-20",
      |      "accountingDate" : "2017-11-20",
      |      "balanceAfter" : 11.5
      |    }
      |  ]
      |}""".stripMargin

  protected val zeroTransactionsReturnedByHelpToSaveJsonString: String =
    """
      |{
      |    "transactions": []
      |}
    """.stripMargin

  protected val zeroTransactionsReturnedByMobileHelpToSaveJsonString: String = {
    """
      |{
      |    "transactions": []
      |}
    """.stripMargin
  }

  protected val transactionsWithOver50PoundDebitReturnedByHelpToSaveJsonString: String = {
    """
      |{
      |    "transactions": [
      |        {
      |            "operation": "credit",
      |            "amount": 50,
      |            "transactionDate": "2014-03-01",
      |            "accountingDate": "2014-03-01",
      |            "description": "Debit card online deposit",
      |            "transactionReference": "B8C29ZY4A00A0018",
      |            "balanceAfter": 50
      |        },
      |        {
      |            "operation": "credit",
      |            "amount": 50,
      |            "transactionDate": "2014-04-01",
      |            "accountingDate": "2014-04-01",
      |            "description": "Debit card online deposit",
      |            "transactionReference": "B8C29ZY4A00A0018",
      |            "balanceAfter": 100
      |        },
      |        {
      |            "operation": "debit",
      |            "amount": 100,
      |            "transactionDate": "2014-05-01",
      |            "accountingDate": "2014-05-01",
      |            "description": "BACS payment",
      |            "transactionReference": "B8C29ZY4A00A0018",
      |            "balanceAfter": 0
      |        }
      |    ]
      |}
    """.stripMargin
  }

  protected val transactionsWithOver50PoundDebitReturnedByMobileHelpToSaveJsonString: String = {
    """
      |{
      |    "transactions": [
      |        {
      |            "operation": "debit",
      |            "amount": 100,
      |            "transactionDate": "2014-05-01",
      |            "accountingDate": "2014-05-01",
      |            "balanceAfter": 0
      |        },
      |        {
      |            "operation": "credit",
      |            "amount": 50,
      |            "transactionDate": "2014-04-01",
      |            "accountingDate": "2014-04-01",
      |            "balanceAfter": 100
      |        },
      |        {
      |            "operation": "credit",
      |            "amount": 50,
      |            "transactionDate": "2014-03-01",
      |            "accountingDate": "2014-03-01",
      |            "balanceAfter": 50
      |        }
      |    ]
      |}
    """.stripMargin
  }

  protected val multipleTransactionsWithinSameMonthAndDayReturnedByHelpToSaveJsonString: String = {
    """
      |{
      |    "transactions": [
      |        {
      |            "operation": "credit",
      |            "amount": 50,
      |            "transactionDate": "2014-03-01",
      |            "accountingDate": "2014-03-01",
      |            "description": "Debit card online deposit",
      |            "transactionReference": "B8C29ZY4A00A0018",
      |            "balanceAfter": 50
      |        },
      |        {
      |            "operation": "credit",
      |            "amount": 50,
      |            "transactionDate": "2014-03-01",
      |            "accountingDate": "2014-03-01",
      |            "description": "Debit card online deposit",
      |            "transactionReference": "B8C29ZY4A00A0018",
      |            "balanceAfter": 100
      |        },
      |        {
      |            "operation": "credit",
      |            "amount": 50,
      |            "transactionDate": "2014-03-01",
      |            "accountingDate": "2014-03-01",
      |            "description": "Debit card online deposit",
      |            "transactionReference": "B8C29ZY4A00A0018",
      |            "balanceAfter": 150
      |        },
      |        {
      |            "operation": "credit",
      |            "amount": 50,
      |            "transactionDate": "2014-03-02",
      |            "accountingDate": "2014-03-02",
      |            "description": "Debit card online deposit",
      |            "transactionReference": "B8C29ZY4A00A0018",
      |            "balanceAfter": 200
      |        },
      |        {
      |            "operation": "credit",
      |            "amount": 50,
      |            "transactionDate": "2014-03-02",
      |            "accountingDate": "2014-03-02",
      |            "description": "Debit card online deposit",
      |            "transactionReference": "B8C29ZY4A00A0018",
      |            "balanceAfter": 250
      |        },
      |        {
      |            "operation": "debit",
      |            "amount": 10,
      |            "transactionDate": "2014-03-02",
      |            "accountingDate": "2014-03-02",
      |            "description": "BACS payment",
      |            "transactionReference": "B8C29ZY4A00A0018",
      |            "balanceAfter": 240
      |        },
      |        {
      |            "operation": "debit",
      |            "amount": 5,
      |            "transactionDate": "2014-03-02",
      |            "accountingDate": "2014-03-02",
      |            "description": "BACS payment",
      |            "transactionReference": "B8C29ZY4A00A0018",
      |            "balanceAfter": 235
      |        },
      |        {
      |            "operation": "debit",
      |            "amount": 15,
      |            "transactionDate": "2014-03-04",
      |            "accountingDate": "2014-03-04",
      |            "description": "BACS payment",
      |            "transactionReference": "B8C29ZY4A00A0018",
      |            "balanceAfter": 220
      |        }
      |    ]
      |}
    """.stripMargin
  }

  protected val multipleTransactionsWithinSameMonthAndDayReturnedByMobileHelpToSaveJsonString: String = {
    """
      |{
      |    "transactions": [
      |        {
      |            "operation": "debit",
      |            "amount": 15,
      |            "transactionDate": "2014-03-04",
      |            "accountingDate": "2014-03-04",
      |            "balanceAfter": 220
      |        },
      |        {
      |            "operation": "debit",
      |            "amount": 5,
      |            "transactionDate": "2014-03-02",
      |            "accountingDate": "2014-03-02",
      |            "balanceAfter": 235
      |        },
      |        {
      |            "operation": "debit",
      |            "amount": 10,
      |            "transactionDate": "2014-03-02",
      |            "accountingDate": "2014-03-02",
      |            "balanceAfter": 240
      |        },
      |        {
      |            "operation": "credit",
      |            "amount": 50,
      |            "transactionDate": "2014-03-02",
      |            "accountingDate": "2014-03-02",
      |            "balanceAfter": 250
      |        },
      |        {
      |            "operation": "credit",
      |            "amount": 50,
      |            "transactionDate": "2014-03-02",
      |            "accountingDate": "2014-03-02",
      |            "balanceAfter": 200
      |        },
      |        {
      |            "operation": "credit",
      |            "amount": 50,
      |            "transactionDate": "2014-03-01",
      |            "accountingDate": "2014-03-01",
      |            "balanceAfter": 150
      |        },
      |        {
      |            "operation": "credit",
      |            "amount": 50,
      |            "transactionDate": "2014-03-01",
      |            "accountingDate": "2014-03-01",
      |            "balanceAfter": 100
      |        },
      |        {
      |            "operation": "credit",
      |            "amount": 50,
      |            "transactionDate": "2014-03-01",
      |            "accountingDate": "2014-03-01",
      |            "balanceAfter": 50
      |        }
      |    ]
      |}
    """.stripMargin
  }

  protected val dateDynamicTransactionsReturnedByHelpToSaveJsonString: String =
    s"""
       |{
       |  "transactions" : [ {
       |    "operation" : "credit",
       |    "amount" : 11.5,
       |    "transactionDate" : "${LocalDate.now().minusMonths(15)}",
       |    "accountingDate" : "${LocalDate.now().minusMonths(15)}",
       |    "description" : "Debit card online deposit",
       |    "transactionReference" : "A1A11AA1A00A0034",
       |    "balanceAfter" : 11.5
       |  }, {
       |    "operation" : "debit",
       |    "amount" : 1.01,
       |    "transactionDate" : "${LocalDate.now().minusMonths(14)}",
       |    "accountingDate" : "${LocalDate.now().minusMonths(14)}",
       |    "description" : "BACS payment",
       |    "transactionReference" : "A1A11AA1A00A000I",
       |    "balanceAfter" : 10.49
       |  }, {
       |    "operation" : "debit",
       |    "amount" : 1.11,
       |    "transactionDate" : "${LocalDate.now().minusMonths(13)}",
       |    "accountingDate" : "${LocalDate.now().minusMonths(13)}",
       |    "description" : "BACS payment",
       |    "transactionReference" : "A1A11AA1A00A000G",
       |    "balanceAfter" : 9.38
       |  }, {
       |    "operation" : "credit",
       |    "amount" : 11.5,
       |    "transactionDate" : "${LocalDate.now().minusMonths(5)}",
       |    "accountingDate" : "${LocalDate.now().minusMonths(5)}",
       |    "description" : "Debit card online deposit",
       |    "transactionReference" : "A1A11AA1A00A0034",
       |    "balanceAfter" : 11.5
       |  }, {
       |    "operation" : "debit",
       |    "amount" : 1.01,
       |    "transactionDate" : "${LocalDate.now().minusMonths(4)}",
       |    "accountingDate" : "${LocalDate.now().minusMonths(4)}",
       |    "description" : "BACS payment",
       |    "transactionReference" : "A1A11AA1A00A000I",
       |    "balanceAfter" : 10.49
       |  }, {
       |    "operation" : "debit",
       |    "amount" : 1.11,
       |    "transactionDate" : "${LocalDate.now().minusMonths(3)}",
       |    "accountingDate" : "${LocalDate.now().minusMonths(3)}",
       |    "description" : "BACS payment",
       |    "transactionReference" : "A1A11AA1A00A000G",
       |    "balanceAfter" : 9.38
       |  }, {
       |    "operation" : "credit",
       |    "amount" : 25,
       |    "transactionDate" : "${LocalDate.now().minusMonths(3)}",
       |    "accountingDate" : "${LocalDate.now().minusMonths(3)}",
       |    "description" : "BACS payment",
       |    "transactionReference" : "A1A11AA1A00A000G",
       |    "balanceAfter" : 9.38
       |  }, {
       |    "operation" : "credit",
       |    "amount" : 1.11,
       |    "transactionDate" : "${LocalDate.now().minusMonths(2)}",
       |    "accountingDate" : "${LocalDate.now().minusMonths(2)}",
       |    "description" : "Reinstatement Adjustment",
       |    "transactionReference" : "A1A11AA1A00A000G",
       |    "balanceAfter" : 10.49
       |  }, {
       |    "operation" : "credit",
       |    "amount" : 50,
       |    "transactionDate" : "${LocalDate.now().minusMonths(1)}",
       |    "accountingDate" : "${LocalDate.now().minusMonths(1)}",
       |    "description" : "Debit card online deposit",
       |    "transactionReference" : "A1A11AA1A00A0059",
       |    "balanceAfter" : 60.49
       |  } ]
       |}""".stripMargin

  // help-to-save returns transactions earliest first, but mobile-help-to-save returns them latest-first because that is the order the apps need to display them in
  val transactionsSortedInHelpToSaveOrder: Transactions = Transactions(
    Seq(
      Transaction(Credit,
                  BigDecimal("11.50"),
                  LocalDate.parse("2017-11-20"),
                  LocalDate.parse("2017-11-20"),
                  BigDecimal("11.50")),
      Transaction(Debit,
                  BigDecimal("1.01"),
                  LocalDate.parse("2017-11-27"),
                  LocalDate.parse("2017-11-27"),
                  BigDecimal("10.49")),
      Transaction(Debit,
                  BigDecimal("1.11"),
                  LocalDate.parse("2017-11-27"),
                  LocalDate.parse("2017-11-27"),
                  BigDecimal("9.38")),
      Transaction(Credit,
                  BigDecimal("1.11"),
                  LocalDate.parse("2017-11-27"),
                  LocalDate.parse("2017-12-04"),
                  BigDecimal("10.49")),
      Transaction(Credit,
                  BigDecimal(50),
                  LocalDate.parse("2018-04-10"),
                  LocalDate.parse("2018-04-10"),
                  BigDecimal("60.49"))
    )
  )

  val transactionsSortedInMobileHelpToSaveOrder: Transactions = Transactions(
    Seq(
      Transaction(Credit,
                  BigDecimal(50),
                  LocalDate.parse("2018-04-10"),
                  LocalDate.parse("2018-04-10"),
                  BigDecimal("60.49")),
      Transaction(Credit,
                  BigDecimal("1.11"),
                  LocalDate.parse("2017-11-27"),
                  LocalDate.parse("2017-12-04"),
                  BigDecimal("10.49")),
      Transaction(Debit,
                  BigDecimal("1.11"),
                  LocalDate.parse("2017-11-27"),
                  LocalDate.parse("2017-11-27"),
                  BigDecimal("9.38")),
      Transaction(Debit,
                  BigDecimal("1.01"),
                  LocalDate.parse("2017-11-27"),
                  LocalDate.parse("2017-11-27"),
                  BigDecimal("10.49")),
      Transaction(Credit,
                  BigDecimal("11.50"),
                  LocalDate.parse("2017-11-20"),
                  LocalDate.parse("2017-11-20"),
                  BigDecimal("11.50"))
    )
  )

  val transactionsDateDynamic: Transactions = Transactions(
    Seq(
      Transaction(Credit,
                  BigDecimal("11.50"),
                  LocalDate.now().minusYears(2),
                  LocalDate.now().minusMonths(2),
                  BigDecimal("11.50")),
      Transaction(Debit,
                  BigDecimal("41.60"),
                  LocalDate.now().minusMonths(16),
                  LocalDate.now().minusMonths(16),
                  BigDecimal("60.00")),
      Transaction(Debit,
                  BigDecimal("11.50"),
                  LocalDate.now().minusMonths(15),
                  LocalDate.now().minusMonths(15),
                  BigDecimal("11.50")),
      Transaction(Credit,
                  BigDecimal("11.50"),
                  LocalDate.now().minusMonths(5),
                  LocalDate.now().minusMonths(5),
                  BigDecimal("11.50")),
      Transaction(Debit,
                  BigDecimal("1.01"),
                  LocalDate.now().minusMonths(5),
                  LocalDate.now().minusMonths(5),
                  BigDecimal("10.49")),
      Transaction(Debit,
                  BigDecimal("1.11"),
                  LocalDate.now().minusMonths(4),
                  LocalDate.now().minusMonths(4),
                  BigDecimal("9.38")),
      Transaction(Credit,
                  BigDecimal("1.11"),
                  LocalDate.now().minusMonths(3),
                  LocalDate.now().minusMonths(3),
                  BigDecimal("10.49")),
      Transaction(Credit,
                  BigDecimal(50),
                  LocalDate.now().minusMonths(2),
                  LocalDate.now().minusMonths(2),
                  BigDecimal("60.49")),
      Transaction(Debit,
                  BigDecimal(50),
                  LocalDate.now().minusMonths(2),
                  LocalDate.now().minusMonths(2),
                  BigDecimal("110.49")),
      Transaction(Credit,
                  BigDecimal(75),
                  LocalDate.now().minusMonths(1),
                  LocalDate.now().minusMonths(1),
                  BigDecimal("35.49")),
      Transaction(Debit,
                  BigDecimal(45.50),
                  LocalDate.now().minusMonths(1),
                  LocalDate.now().minusMonths(1),
                  BigDecimal("80.99")),
      Transaction(Debit,
                  BigDecimal(30),
                  LocalDate.now().minusMonths(1),
                  LocalDate.now().minusMonths(1),
                  BigDecimal("300.00"))
    )
  )

  def transactionsWithAverageSavingsRate(averageSavingRate: BigDecimal): Transactions = Transactions(
    Seq(
      Transaction(Credit,
        averageSavingRate,
        LocalDate.now().minusMonths(6),
        LocalDate.now().minusMonths(6),
        averageSavingRate),
      Transaction(Credit,
        averageSavingRate,
        LocalDate.now().minusMonths(5),
        LocalDate.now().minusMonths(5),
        averageSavingRate),
      Transaction(Credit,
        averageSavingRate,
        LocalDate.now().minusMonths(4),
        LocalDate.now().minusMonths(4),
        averageSavingRate),
      Transaction(Credit,
        averageSavingRate,
        LocalDate.now().minusMonths(3),
        LocalDate.now().minusMonths(3),
        averageSavingRate),
      Transaction(Credit,
        averageSavingRate,
        LocalDate.now().minusMonths(2),
        LocalDate.now().minusMonths(2),
        averageSavingRate),
      Transaction(Credit,
        averageSavingRate,
        LocalDate.now().minusMonths(1),
        LocalDate.now().minusMonths(1),
        averageSavingRate),
    )
  )
}
