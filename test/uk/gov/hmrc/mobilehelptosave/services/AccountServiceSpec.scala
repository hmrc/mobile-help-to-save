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

package uk.gov.hmrc.mobilehelptosave.services

import org.joda.time.LocalDate
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OneInstancePerTest, OptionValues, WordSpec}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveProxyConnector, NsiAccount, NsiBonusTerm, NsiCurrentInvestmentMonth}
import uk.gov.hmrc.mobilehelptosave.domain.BonusTerm
import uk.gov.hmrc.mobilehelptosave.support.LoggerStub

import scala.concurrent.ExecutionContext.Implicits.{global => passedEc}
import scala.concurrent.{ExecutionContext, Future}

class AccountServiceSpec extends WordSpec with Matchers
  with FutureAwaits with DefaultAwaitTimeout with OptionValues
  with MockFactory with OneInstancePerTest with LoggerStub {

  private val generator = new Generator(0)
  private val nino = generator.nextNino

  private val testNsiAccount = NsiAccount(
    accountClosedFlag = "",
    accountBlockingCode = "00",
    clientBlockingCode = "00",
    accountBalance = 0,
    currentInvestmentMonth = NsiCurrentInvestmentMonth(0, 0, new LocalDate(1900, 1, 1)),
    terms = Seq.empty
  )

  private implicit val passedHc: HeaderCarrier = HeaderCarrier()

  "account" should {
    "return account details for open, unblocked account" in {
      val connector = fakeHelpToSaveProxyConnector(nino, Some(testNsiAccount.copy(accountBalance = BigDecimal("123.45"))))
      val service = new AccountServiceImpl(logger, connector)

      val returnedAccount = await(service.account(nino)).value
      returnedAccount.isClosed shouldBe false
      returnedAccount.balance shouldBe BigDecimal("123.45")
      returnedAccount.blocked.unspecified shouldBe false

      (slf4jLoggerStub.warn(_: String)).verify(*).never()
    }

    """accept accountClosedFlag = " " (space) to mean not closed""" in {
      val connector = fakeHelpToSaveProxyConnector(nino, Some(testNsiAccount.copy(accountClosedFlag = " ", accountBalance = BigDecimal("123.45"))))
      val service = new AccountServiceImpl(logger, connector)

      val returnedAccount = await(service.account(nino)).value
      returnedAccount.isClosed shouldBe false
      returnedAccount.balance shouldBe BigDecimal("123.45")

      (slf4jLoggerStub.warn(_: String)).verify(*).never()
    }

    """return blocking.unspecified = true when accountBlockingCode is not "00"""" in {
      val connector = fakeHelpToSaveProxyConnector(nino, Some(testNsiAccount.copy(accountBlockingCode = "01")))
      val service = new AccountServiceImpl(logger, connector)

      val returnedAccount = await(service.account(nino)).value
      returnedAccount.blocked.unspecified shouldBe true

      (slf4jLoggerStub.warn(_: String)).verify(*).never()
    }

    """return blocking.unspecified = true when clientBlockingCode is not "00"""" in {
      val connector = fakeHelpToSaveProxyConnector(nino, Some(testNsiAccount.copy(clientBlockingCode = "01")))
      val service = new AccountServiceImpl(logger, connector)

      val returnedAccount = await(service.account(nino)).value
      returnedAccount.blocked.unspecified shouldBe true

      (slf4jLoggerStub.warn(_: String)).verify(*).never()
    }

    "log warning for unknown accountClosedFlag values" in {
      val connector = fakeHelpToSaveProxyConnector(nino, Some(testNsiAccount.copy(accountClosedFlag = "O", accountBalance = BigDecimal("123.45"))))
      val service = new AccountServiceImpl(logger, connector)

      val returnedAccount = await(service.account(nino)).value
      returnedAccount.isClosed shouldBe false

      (slf4jLoggerStub.warn(_: String)) verify """Unknown value for accountClosedFlag: "O""""
    }

    "return account details for closed account" in {
      val connector = fakeHelpToSaveProxyConnector(nino, Some(testNsiAccount.copy(
        accountClosedFlag = "C",
        accountBalance = BigDecimal("0.00"),
        accountClosureDate = Some(new LocalDate(2018, 2, 16)),
        accountClosingBalance = Some(BigDecimal("123.45"))
      )))
      val service = new AccountServiceImpl(logger, connector)

      val returnedAccount = await(service.account(nino)).value
      returnedAccount.isClosed shouldBe true
      returnedAccount.balance shouldBe BigDecimal(0)
      returnedAccount.closingBalance shouldBe Some(BigDecimal("123.45"))
      returnedAccount.closureDate shouldBe Some(new LocalDate(2018, 2, 16))
    }

    "return details for current month" in {
      val connector = fakeHelpToSaveProxyConnector(nino, Some(testNsiAccount.copy(
        currentInvestmentMonth = NsiCurrentInvestmentMonth(
          investmentRemaining = BigDecimal("12.34"),
          investmentLimit = 50,
          endDate = new LocalDate(2019, 6, 23)
        ))))
      val service = new AccountServiceImpl(logger, connector)

      val account = await(service.account(nino)).value
      account.paidInThisMonth shouldBe BigDecimal("37.66")
      account.canPayInThisMonth shouldBe BigDecimal("12.34")
      account.maximumPaidInThisMonth shouldBe BigDecimal(50)
      account.thisMonthEndDate shouldBe new LocalDate(2019, 6, 23)
    }

    "return None when the payment amounts for current month don't make sense because investmentRemaining > investmentLimit" in {
      val connector = fakeHelpToSaveProxyConnector(nino, Some(testNsiAccount.copy(
        currentInvestmentMonth = testNsiAccount.currentInvestmentMonth.copy(investmentRemaining = BigDecimal("50.01"), investmentLimit = 50))))
      val service = new AccountServiceImpl(logger, connector)

      await(service.account(nino)) shouldBe None

      (slf4jLoggerStub.warn(_: String)) verify """investmentRemaining = 50.01 and investmentLimit = 50 values returned by NS&I don't make sense because they imply a negative amount paid in this month"""
    }

    "return payment amounts for current month when investmentRemaining == investmentLimit (boundary case for previous test)" in {
      val connector = fakeHelpToSaveProxyConnector(nino, Some(testNsiAccount.copy(
        currentInvestmentMonth = testNsiAccount.currentInvestmentMonth.copy(investmentRemaining = 50, investmentLimit = 50))))
      val service = new AccountServiceImpl(logger, connector)

      val account = await(service.account(nino)).value
      account.paidInThisMonth shouldBe BigDecimal(0)
      account.canPayInThisMonth shouldBe BigDecimal(50)
      account.maximumPaidInThisMonth shouldBe BigDecimal(50)
    }

    "return bonus information including calculated bonusPaidOnOrAfterDate" in {
      val connector = fakeHelpToSaveProxyConnector(nino, Some(testNsiAccount.copy(
        terms = Seq(
          NsiBonusTerm(termNumber = 1, endDate = new LocalDate(2020, 10, 22), bonusEstimate = BigDecimal("65.43"), bonusPaid = 0)))))

      val service = new AccountServiceImpl(logger, connector)

      await(service.account(nino)).value.bonusTerms shouldBe Seq(
        BonusTerm(bonusEstimate = BigDecimal("65.43"), bonusPaid = 0, endDate = new LocalDate(2020, 10, 22), bonusPaidOnOrAfterDate = new LocalDate(2020, 10, 23))
      )
    }

    "sort the bonus terms by termNumber" in {
      val connector = fakeHelpToSaveProxyConnector(nino, Some(testNsiAccount.copy(
        accountBalance = BigDecimal("200.34"),
        terms = Seq(
          NsiBonusTerm(termNumber = 2, endDate = new LocalDate(2021, 12, 31), bonusEstimate = 67, bonusPaid = 0),
          NsiBonusTerm(termNumber = 1, endDate = new LocalDate(2019, 12, 31), bonusEstimate = BigDecimal("123.45"), bonusPaid = BigDecimal("123.45"))
        ))))

      val service = new AccountServiceImpl(logger, connector)

      await(service.account(nino)).value.bonusTerms shouldBe Seq(
        BonusTerm(bonusEstimate = BigDecimal("123.45"), bonusPaid = BigDecimal("123.45"), endDate = new LocalDate(2019, 12, 31), bonusPaidOnOrAfterDate = new LocalDate(2020, 1, 1)),
        BonusTerm(bonusEstimate = 67, bonusPaid = 0, endDate = new LocalDate(2021, 12, 31), bonusPaidOnOrAfterDate = new LocalDate(2022, 1, 1))
      )
    }

    "return None when the NS&I account cannot be retrieved" in {
      val connector = fakeHelpToSaveProxyConnector(nino, None)
      val service = new AccountServiceImpl(logger, connector)

      await(service.account(nino)) shouldBe None
    }
  }

  private def fakeHelpToSaveProxyConnector(expectedNino: Nino, account: Option[NsiAccount]) = new HelpToSaveProxyConnector {
    override def nsiAccount(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[NsiAccount]] = {
      nino shouldBe expectedNino
      hc shouldBe passedHc
      ec shouldBe passedEc

      Future successful account
    }
  }
}
