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

import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OneInstancePerTest, WordSpec}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.WriteResult
import reactivemongo.core.errors.GenericDatabaseException
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.connectors.{Payment, TaxCreditsBrokerConnector}
import uk.gov.hmrc.mobilehelptosave.domain.NinoWithoutWtc
import uk.gov.hmrc.mobilehelptosave.metrics.FakeMobileHelpToSaveMetrics
import uk.gov.hmrc.mobilehelptosave.repos.FakeNinoWithoutWtcRepository
import uk.gov.hmrc.mobilehelptosave.support.LoggerStub

import scala.concurrent.ExecutionContext.Implicits.{global => passedEc}
import scala.concurrent.{ExecutionContext, Future}

class TaxCreditsServiceSpec extends WordSpec with Matchers with FutureAwaits with DefaultAwaitTimeout with MockFactory with OneInstancePerTest with LoggerStub {
  private val fixedClock = new FixedFakeClock(DateTime.parse("2017-11-22T10:20:30"))

  private val generator = new Generator(0)
  private val nino = generator.nextNino

  private implicit val passedHc: HeaderCarrier = HeaderCarrier()

  "hasRecentWtcPayments" when {
    "there are no previous payments" should {
      "return false" in {
        resultForPaymentsShouldBe(Seq.empty, expectedResult = false)
      }

      "only call tax-credits-broker once when called multiple times" in {
        val connector = fakeTaxCreditsBrokerConnector(nino, Some(Seq.empty))
        val metrics = FakeMobileHelpToSaveMetrics()
        val service = new TaxCreditsServiceImpl(logger, connector, metrics, new FakeNinoWithoutWtcRepository(), fixedClock)

        await(service.hasRecentWtcPayments(nino)) shouldBe Some(false)
        connector.callCount shouldBe 1
        metrics.taxCreditsBrokerCallCounter.getCount shouldBe 1
        metrics.taxCreditsCacheHitCounter.getCount shouldBe 0

        await(service.hasRecentWtcPayments(nino)) shouldBe Some(false)
        connector.callCount shouldBe 1
        metrics.taxCreditsBrokerCallCounter.getCount shouldBe 1
        metrics.taxCreditsCacheHitCounter.getCount shouldBe 1
      }

      "skip caching (risk extra calls tax-credits-broker rather than showing errors to users) when there is a MongoDB error querying the cache" in {
        val connector = fakeTaxCreditsBrokerConnector(nino, Some(Seq.empty))
        val exceptionThrownByFind = GenericDatabaseException("Test exception", None)
        val repository = new FakeNinoWithoutWtcRepository() {
          override def findById(id: Nino, readPreference: ReadPreference)(implicit ec: ExecutionContext): Future[Option[NinoWithoutWtc]] =
            Future failed exceptionThrownByFind
        }
        val service = new TaxCreditsServiceImpl(logger, connector, FakeMobileHelpToSaveMetrics(), repository, fixedClock)

        await(service.hasRecentWtcPayments(nino)) shouldBe Some(false)
        await(service.hasRecentWtcPayments(nino)) shouldBe Some(false)

        (slf4jLoggerStub.warn(_: String, _: Throwable)).verify(
          "Couldn't check tax credits cache, this may result in extra calls to tax-credits-broker",
          exceptionThrownByFind
        ).twice()
      }

      "skip caching when there is a MongoDB error updating the cache" in {
        val connector = fakeTaxCreditsBrokerConnector(nino, Some(Seq.empty))
        val exceptionThrownByInsert = GenericDatabaseException("Test exception", None)
        val repository = new FakeNinoWithoutWtcRepository() {

          override def insert(entity: NinoWithoutWtc)(implicit ec: ExecutionContext): Future[WriteResult] =
            Future failed exceptionThrownByInsert
        }
        val service = new TaxCreditsServiceImpl(logger, connector, FakeMobileHelpToSaveMetrics(), repository, fixedClock)

        await(service.hasRecentWtcPayments(nino)) shouldBe Some(false)
        await(service.hasRecentWtcPayments(nino)) shouldBe Some(false)

        (slf4jLoggerStub.warn(_: String, _: Throwable)).verify(
          "Couldn't update tax credits cache, this may result in extra calls to tax-credits-broker",
          exceptionThrownByInsert
        ).twice()
      }
    }

    "there are previous payments with negative amount less than 30 days ago" should {
      "return false" in {
        resultForPaymentsShouldBe(Seq(Payment(BigDecimal("-0.01"), fixedClock.now().minusDays(29))), expectedResult = false)
      }
    }

    "there are previous payments with amount £0 less than 30 days ago" should {
      "return false" in {
        resultForPaymentsShouldBe(Seq(Payment(BigDecimal(0), fixedClock.now().minusDays(29))), expectedResult = false)
      }
    }

    "there are previous payments with positive amount more than 30 days ago" should {
      "return false" in {
        resultForPaymentsShouldBe(Seq(Payment(BigDecimal("0.01"), fixedClock.now().minusDays(31))), expectedResult = false)
      }
    }

    "there are previous payments with positive amount exactly 30 days ago" should {
      "return true" in {
        resultForPaymentsShouldBe(Seq(Payment(BigDecimal("0.01"), fixedClock.now().minusDays(30))), expectedResult = true)
      }
    }

    "there are previous payments with positive amount less than 30 days ago" should {
      val payments = Seq(Payment(BigDecimal("0.01"), fixedClock.now().minusDays(29)))

      "return true" in {
        resultForPaymentsShouldBe(payments, expectedResult = true)
      }

      "continue to return true when called multiple times (not be broken by caching)" in {
        val metrics = FakeMobileHelpToSaveMetrics()
        val service = new TaxCreditsServiceImpl(logger, fakeTaxCreditsBrokerConnector(nino, Some(payments)), metrics, new FakeNinoWithoutWtcRepository(), fixedClock)

        await(service.hasRecentWtcPayments(nino)) shouldBe Some(true)
        metrics.taxCreditsBrokerCallCounter.getCount shouldBe 1
        metrics.taxCreditsCacheHitCounter.getCount shouldBe 0
        await(service.hasRecentWtcPayments(nino)) shouldBe Some(true)
        metrics.taxCreditsBrokerCallCounter.getCount shouldBe 2
        metrics.taxCreditsCacheHitCounter.getCount shouldBe 0
      }
    }

    "there are multiple payments, some more than and some less than 30 days ago" should {
      "return true" in {
        resultForPaymentsShouldBe(
          Seq(
            Payment(BigDecimal("0.01"), fixedClock.now().minusDays(31)),
            Payment(BigDecimal("0.01"), fixedClock.now().minusDays(29))
          ),
          expectedResult = true)
      }
    }

    "there are previous payments with dates in the future" should {
      "return true and log a warning" in {
        resultForPaymentsShouldBe(
          Seq(Payment(BigDecimal("0.01"), fixedClock.now().plusDays(1))),
          expectedResult = true)

        (slf4jLoggerStub.warn(_: String)) verify """Previous payment has date that isn't in the past: "2017-11-23T10:20:30.000Z""""
      }
    }

    "previous payments are unknown" should {
      "return None" in {
        val service = new TaxCreditsServiceImpl(logger, fakeTaxCreditsBrokerConnector(nino, None), FakeMobileHelpToSaveMetrics(), new FakeNinoWithoutWtcRepository(), fixedClock)
        await(service.hasRecentWtcPayments(nino)) shouldBe None
      }
    }
  }

  private def resultForPaymentsShouldBe(payments: Seq[Payment], expectedResult: Boolean): Unit = {
    val service = new TaxCreditsServiceImpl(logger, fakeTaxCreditsBrokerConnector(nino, Some(payments)), FakeMobileHelpToSaveMetrics(), new FakeNinoWithoutWtcRepository(), fixedClock)
    await(service.hasRecentWtcPayments(nino)) shouldBe Some(expectedResult)
  }

  private def fakeTaxCreditsBrokerConnector(expectedNino: Nino, maybePreviousPayments: Option[Seq[Payment]]) =
    new FakeTaxCreditsBrokerConnector(expectedNino, maybePreviousPayments)

  private class FakeTaxCreditsBrokerConnector(expectedNino: Nino, maybePreviousPayments: Option[Seq[Payment]]) extends TaxCreditsBrokerConnector {
    private var _callCount = 0

    def callCount: Int = _callCount

    override def previousPayments(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Seq[Payment]]] = {
      _callCount = _callCount + 1

      nino shouldBe expectedNino
      hc shouldBe passedHc
      ec shouldBe passedEc
      Future successful maybePreviousPayments
    }
  }

}
