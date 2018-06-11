package uk.gov.hmrc.mobilehelptosave.controllers

import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OneInstancePerTest, WordSpec}
import play.api.mvc.{Request, Result, Results}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.TestData
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveConnector
import uk.gov.hmrc.mobilehelptosave.domain.InternalAuthId

import scala.concurrent.{ExecutionContext, Future}

class TransactionControllerSpec
  extends WordSpec
    with Matchers
    with MockFactory
    with OneInstancePerTest
    with FutureAwaits
    with TestData
    with DefaultAwaitTimeout {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val generator = new Generator(0)
  private val nino = generator.nextNino
  private val internalAuthId = InternalAuthId("some-internal-auth-id")
  private val helpToSaveConnector = mock[HelpToSaveConnector]

  private class AlwaysAuthorisedWithIds(id: InternalAuthId, nino: Nino) extends AuthorisedWithIds {
    override protected def refine[A](request: Request[A]): Future[Either[Result, RequestWithIds[A]]] =
      Future successful Right(new RequestWithIds(id, nino, request))
  }

  "getTransactions" should {

    "pass NINO obtained from auth into the HelpToSaveConnector" in {
      val controller = new TransactionController(helpToSaveConnector, new AlwaysAuthorisedWithIds(internalAuthId, nino))

      controller.getTransactions(nino.value)

      (helpToSaveConnector.getTransactions(_:String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino.value, *, *)
        .returning(Future successful Right(transactions))
    }
  }
}
