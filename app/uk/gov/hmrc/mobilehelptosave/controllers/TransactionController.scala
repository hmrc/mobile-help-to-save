package uk.gov.hmrc.mobilehelptosave.controllers

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveConnector
import uk.gov.hmrc.play.bootstrap.controller.BaseController

@Singleton
class TransactionController @Inject()
(
  helpToSaveConnector: HelpToSaveConnector,
  authorisedWithIds: AuthorisedWithIds
) extends BaseController {

  def getTransactions(nino: String) = authorisedWithIds.async { implicit request =>
    ???
  }
}
