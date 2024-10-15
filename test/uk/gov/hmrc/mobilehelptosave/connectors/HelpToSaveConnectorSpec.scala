/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.mobilehelptosave.connectors

import java.net.URL
import io.lemonlabs.uri._
import org.mockito.Mockito.when
import play.api.LoggerLike
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HttpResponse, InternalServerException, NotFoundException, UpstreamErrorResponse, _}
import uk.gov.hmrc.mobilehelptosave.config.HelpToSaveConnectorConfig
import uk.gov.hmrc.mobilehelptosave.domain.{EligibilityCheckResponse, EligibilityCheckResult, ErrorInfo, Transactions}
import uk.gov.hmrc.mobilehelptosave.{AccountTestData, TransactionTestData}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

class HelpToSaveConnectorSpec extends HttpClientV2Helper with AccountTestData with TransactionTestData {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val baseUrl = "http://help-to-save-service"

  private val ninoString = "AA000000A"
  override val nino = Nino(ninoString)

  private def isAccountUrlForNino(urlString: String): Boolean = Url.parse(urlString) match {
    case AbsoluteUrl("http",
    Authority(_, Host("help-to-save-service"), None),
    AbsolutePath(Vector("help-to-save", this.ninoString, "account")),
    query,
    _) if query.param("systemId").contains("MDTP-MOBILE") =>
      true
    case _ =>
      info(s"URL failed to match: $urlString")
      false
  }

  private def isTransactionsUrlForNino(urlString: String): Boolean = Url.parse(urlString) match {
    case AbsoluteUrl("http",
    Authority(_, Host("help-to-save-service"), None),
    AbsolutePath(Vector("help-to-save", this.ninoString, "account", "transactions")),
    query,
    _) if query.param("systemId").contains("MDTP-MOBILE") =>
      true
    case _ =>
      info(s"URL failed to match: $urlString")
      false
  }
  private val config: HelpToSaveConnectorConfig = new HelpToSaveConnectorConfig {
    override val helpToSaveBaseUrl: URL = new URL(baseUrl)
  }
  val logger = mock[LoggerLike]
  val connector = new HelpToSaveConnectorImpl(logger, config, mockHttpClient)
  "HelpToSaveConnectorSpec" when {
    "getAccount" should {

      "return a Right (with Account) when the help-to-save service returns a 2xx response" in {
        when(requestBuilderExecute[HelpToSaveAccount])
          .thenReturn(Future.successful(helpToSaveAccount))

        connector.getAccount(nino) onComplete {
          case Success(_) => Right(Some(helpToSaveAccount))
          case Failure(_) =>
        }
      }

      "return a Right (with Account) when the help-to-save service returns a 2xx response with optional fields omitted" in {

        val updatedResponse = helpToSaveAccount.copy(accountHolderEmail = None)
        when(requestBuilderExecute[HelpToSaveAccount])
          .thenReturn(Future.successful(updatedResponse))

        connector.getAccount(nino) onComplete {
          case Success(_) => Right(Some(updatedResponse))
          case Failure(_) =>
        }
      }

      "return Right(None) when the help-to-save service returns a 404 response" in {

        when(requestBuilderExecute[HelpToSaveAccount])
          .thenReturn(Future.failed(new NotFoundException("Not Found")))

        connector.getAccount(nino) onComplete {
          case Success(_) => Right(None)
          case Failure(_) =>
        }
      }

      "return a Left when there is an error connecting to the help-to-save service" in {
        when(requestBuilderExecute[HelpToSaveAccount])
          .thenReturn(Future.successful(helpToSaveAccount))

        connector.getAccount(nino) onComplete {
          case Success(_) => Left(ErrorInfo.General)
          case Failure(_) =>
        }
      }
      "return a Left when the help-to-save service returns a 4xx error" in {
        when(requestBuilderExecute[HelpToSaveAccount])
          .thenReturn(Future.successful(helpToSaveAccount))

        connector.getAccount(nino) onComplete {
          case Success(_) => Left(ErrorInfo.MultipleRequests)
          case Failure(_) =>
        }
      }
      "return a Left when the help-to-save service returns a 5xx error" in {
        when(requestBuilderExecute[HelpToSaveAccount])
          .thenReturn(Future.successful(helpToSaveAccount))

        connector.getAccount(nino) onComplete {
          case Success(_) => Left(ErrorInfo.General)
          case Failure(_) =>
        }
      }
      "return a Left[ErrorInfo] when help-to-save returns JSON that is missing fields that are required according to get_account_by_nino_RESP_schema_V1.0.json" in {
        when(requestBuilderExecute[HelpToSaveAccount])
          .thenReturn(Future.successful(helpToSaveAccount))

        connector.getAccount(nino) onComplete {
          case Success(_) => Left(ErrorInfo.General)
          case Failure(_) =>
        }
      }
    }
    "getTransactions" should {
      val errorMessage = """Couldn't get transaction information from help-to-save service"""
      "return a Left when there is an error connecting to the help-to-save service" in {
        when(requestBuilderExecute[HelpToSaveAccount])
          .thenReturn(Future.successful(helpToSaveAccount))

        connector.getTransactions(nino) onComplete {
          case Success(_) => Left(ErrorInfo.General)
          case Failure(_) =>
        }
      }

      "return a Left when the help-to-save service returns a 4xx error" in {
        when(requestBuilderExecute[HelpToSaveAccount])
          .thenReturn(Future.successful(helpToSaveAccount))

        connector.getTransactions(nino) onComplete {
          case Success(_) => Left(ErrorInfo.MultipleRequests)
          case Failure(_) =>
        }
      }
      "return a Left when the help-to-save service returns a 5xx error" in {
        when(requestBuilderExecute[HelpToSaveAccount])
          .thenReturn(Future.successful(helpToSaveAccount))

        connector.getTransactions(nino) onComplete {
          case Success(_) => Left(ErrorInfo.General)
          case Failure(_) =>
        }
      }
      "return a Right (with Transactions) when the help-to-save service returns a 2xx response" in {
        when(requestBuilderExecute[HelpToSaveAccount])
          .thenReturn(Future.successful(helpToSaveAccount))

        connector.getTransactions(nino) onComplete {
          case Success(_) => Right(transactionsSortedInHelpToSaveOrder)
          case Failure(_) =>
        }
      }
      "return Left(AccountNotFound) when the help-to-save service returns a 404 response" in {
        when(requestBuilderExecute[HelpToSaveAccount])
          .thenReturn(Future.successful(helpToSaveAccount))

        connector.getTransactions(nino) onComplete {
          case Success(_) => Left(ErrorInfo.AccountNotFound)
          case Failure(_) =>
        }
      }

    }
    "enrolmentStatus" should {
      "return a Left when there is an error connecting to the help-to-save service" in {
        when(requestBuilderExecute[HelpToSaveAccount])
          .thenReturn(Future.successful(helpToSaveAccount))

        connector.checkEligibility() onComplete {
          case Success(_) => Left(ErrorInfo.General)
          case Failure(_) =>
        }
      }

      "return a Left when the help-to-save service returns a 4xx error" in {
        when(requestBuilderExecute[HelpToSaveAccount])
          .thenReturn(Future.successful(helpToSaveAccount))

        connector.checkEligibility() onComplete {
          case Success(_) => Left(ErrorInfo.MultipleRequests)
          case Failure(_) =>
        }
      }
      "return a Left when the help-to-save service returns a 5xx error" in {
        when(requestBuilderExecute[HelpToSaveAccount])
          .thenReturn(Future.successful(helpToSaveAccount))

        connector.checkEligibility() onComplete {
          case Success(_) => Left(ErrorInfo.General)
          case Failure(_) =>
        }
      }
      "return a Right when the help-to-save service returns a 2xx response" in {
        when(requestBuilderExecute[HelpToSaveAccount])
          .thenReturn(Future.successful(helpToSaveAccount))

        connector.checkEligibility() onComplete {
          case Success(_) => Right(true)
          case Failure(_) =>
        }
      }

    }
    "checkEligibility" should {
      "return a Left when there is an error connecting to the help-to-save service" in {
        when(requestBuilderExecute[HelpToSaveAccount])
          .thenReturn(Future.successful(helpToSaveAccount))

        connector.checkEligibility() onComplete {
          case Success(_) => Left(ErrorInfo.General)
          case Failure(_) =>
        }
      }
      "return a Left when the help-to-save service returns a 4xx error" in {
        when(requestBuilderExecute[HelpToSaveAccount])
          .thenReturn(Future.successful(helpToSaveAccount))

        connector.checkEligibility() onComplete {
          case Success(_) => Left(ErrorInfo.MultipleRequests)
          case Failure(_) =>
        }
      }
      "return a Left when the help-to-save service returns a 5xx error" in {
        when(requestBuilderExecute[HelpToSaveAccount])
          .thenReturn(Future.successful(helpToSaveAccount))

        connector.checkEligibility() onComplete {
          case Success(_) => Left(ErrorInfo.General)
          case Failure(_) =>
        }
      }
      "return a Right when the help-to-save service returns a 2xx response" in {
        when(requestBuilderExecute[HelpToSaveAccount])
          .thenReturn(Future.successful(helpToSaveAccount))

        connector.checkEligibility() onComplete {
          case Success(_) => Right(EligibilityCheckResponse(EligibilityCheckResult(result = "", resultCode = 1, reason = "", reasonCode = 6), None))
          case Failure(_) =>
        }
      }
    }
  }
}
