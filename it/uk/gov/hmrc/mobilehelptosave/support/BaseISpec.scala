package uk.gov.hmrc.mobilehelptosave.support

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.ws.WSRequest
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.mobilehelptosave.TransactionTestData

trait BaseISpec
    extends AnyWordSpecLike
    with Matchers
    with TransactionTestData
    with FutureAwaits
    with DefaultAwaitTimeout
    with WireMockSupport
    with OneServerPerSuiteWsClient {

  val acceptJsonHeader:        (String, String) = "Accept"        -> "application/vnd.hmrc.1.0+json"
  val authorisationJsonHeader: (String, String) = "AUTHORIZATION" -> "Bearer 123"
  val journeyId = "27085215-69a4-4027-8f72-b04b10ec16b0"

  val generator = new Generator(0)
  val nino: Nino = generator.nextNino

  def requestWithAuthHeaders(url: String): WSRequest =
    wsUrl(url).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
}
