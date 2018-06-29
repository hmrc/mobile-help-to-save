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

package uk.gov.hmrc.mobilehelptosave.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.http.Status
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.{AccountTestData, TransactionTestData}

object HelpToSaveStub extends AccountTestData with TransactionTestData {
  def currentUserIsEnrolled(): Unit = enrolmentStatusIs(true)
  def currentUserIsNotEnrolled(): Unit = enrolmentStatusIs(false)

  def enrolmentStatusShouldNotHaveBeenCalled(): Unit =
    verify(0, getRequestedFor(urlPathEqualTo("/help-to-save/enrolment-status")))

  def enrolmentStatusReturnsInternalServerError(): Unit =
    stubFor(get(urlPathEqualTo("/help-to-save/enrolment-status"))
      .willReturn(aResponse()
        .withStatus(Status.INTERNAL_SERVER_ERROR)))

  private def enrolmentStatusIs(status: Boolean): Unit =
    stubFor(get(urlPathEqualTo("/help-to-save/enrolment-status"))
      .willReturn(aResponse()
        .withStatus(Status.OK)
        .withBody(
          s"""{"enrolled":$status}"""
        )))

  def transactionsExistForUser(nino: Nino): Unit = {
    stubFor(get(urlPathEqualTo(s"/help-to-save/$nino/account/transactions"))
      .willReturn(aResponse()
        .withStatus(Status.OK)
        .withBody(transactionsReturnedByHelpToSaveJsonString)))
  }

  def zeroTransactionsExistForUser(nino: Nino): Unit = {
    stubFor(get(urlPathEqualTo(s"/help-to-save/$nino/account/transactions"))
      .willReturn(aResponse()
        .withStatus(Status.OK)
        .withBody(zeroTransactionsReturnedByHelpToSaveJsonString)))
  }

  def transactionsWithOver50PoundDebit(nino: Nino): Unit = {
    stubFor(get(urlPathEqualTo(s"/help-to-save/$nino/account/transactions"))
      .willReturn(aResponse()
        .withStatus(Status.OK)
        .withBody(transactionsWithOver50PoundDebitReturnedByHelpToSaveJsonString)))
  }

  def multipleTransactionsWithinSameMonthAndDay(nino: Nino): Unit = {
    stubFor(get(urlPathEqualTo(s"/help-to-save/$nino/account/transactions"))
      .willReturn(aResponse()
        .withStatus(Status.OK)
        .withBody(multipleTransactionsWithinSameMonthAndDayReturnedByHelpToSaveJsonString)))
  }

  def userDoesNotHaveAnHtsAccount(nino: Nino): Unit = {
    stubFor(get(urlPathEqualTo(s"/help-to-save/$nino/account/transactions"))
      .willReturn(aResponse()
        .withStatus(Status.NOT_FOUND)))
  }

  def accountExists(nino: Nino): Unit =
    stubFor(get(urlPathEqualTo(s"/help-to-save/$nino/account"))
      .withQueryParam("systemId", equalTo("MDTP-MOBILE"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(accountReturnedByHelpToSaveJsonString)))

  def closedAccountExists(nino: Nino): Unit =
    stubFor(get(urlPathEqualTo(s"/help-to-save/$nino/account"))
      .withQueryParam("systemId", equalTo("MDTP-MOBILE"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(closedAccountReturnedByHelpToSaveJsonString)))

  def accountReturnsInvalidJson(nino: Nino): Unit =
    stubFor(get(urlPathEqualTo(s"/help-to-save/$nino/account"))
      .withQueryParam("systemId", equalTo("MDTP-MOBILE"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(accountReturnedByHelpToSaveInvalidJsonString)))

  def accountReturnsBadlyFormedJson(nino: Nino): Unit =
    stubFor(get(urlPathEqualTo(s"/help-to-save/$nino/account"))
      .withQueryParam("systemId", equalTo("MDTP-MOBILE"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          """not JSON""".stripMargin
        )))

  def accountReturnsInternalServerError(nino: Nino): Unit =
    stubFor(get(urlPathEqualTo(s"/help-to-save/$nino/account"))
      .withQueryParam("systemId", equalTo("MDTP-MOBILE"))
      .willReturn(aResponse()
        .withStatus(500)))
}
