//
//package uk.gov.hmrc.mobilehelptosave
//
//import play.api.Application
//import play.api.libs.json.JsObject
//import play.api.libs.ws.WSResponse
//import uk.gov.hmrc.domain.Nino
//import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub, ShutteringStub}
//import uk.gov.hmrc.mobilehelptosave.support.{BaseISpec, ComponentSupport}
//
//import java.time.YearMonth
//
//class AccountsISpec extends BaseISpec with NumberVerification with ComponentSupport {
//
//  override implicit lazy val app: Application = appBuilder.build()
//
//  private def loggedInAndEnrolled(nino: Nino) = {
//    ShutteringStub.stubForShutteringDisabled()
//    AuthStub.userIsLoggedIn(nino)
//    HelpToSaveStub.currentUserIsEnrolled()
//    HelpToSaveStub.zeroTransactionsExistForUser(nino)
//  }
//
//  "GET /savings-account/{nino}" should {
//
//    "respond with 200 and the users account data" in {
//      loggedInAndEnrolled(nino)
//      HelpToSaveStub.accountExists(123.45, nino = nino)
//      val response: WSResponse = await(requestWithAuthHeaders(s"/savings-account/$nino?journeyId=$journeyId").get())
//      println(requestWithAuthHeaders(s"/savings-account/$nino?journeyId=$journeyId"))
//      response.status shouldBe 200
//
//      (response.json \ "number").as[String]                   shouldBe "1000000000001"
//      (response.json \ "openedYearMonth").as[String]          shouldBe s"${YearMonth.now().minusYears(3).getYear}-01"
//      (response.json \ "isClosed").as[Boolean]                shouldBe false
//      (response.json \ "blocked" \ "payments").as[Boolean]    shouldBe false
//      (response.json \ "blocked" \ "withdrawals").as[Boolean] shouldBe false
//      (response.json \ "blocked" \ "bonuses").as[Boolean]     shouldBe false
//      shouldBeBigDecimal(response.json \ "balance", BigDecimal("123.45"))
//      shouldBeBigDecimal(response.json \ "paidInThisMonth", BigDecimal("27.88"))
//      shouldBeBigDecimal(response.json \ "canPayInThisMonth", BigDecimal("22.12"))
//      shouldBeBigDecimal(response.json \ "maximumPaidInThisMonth", BigDecimal(50))
//      (response.json \ "thisMonthEndDate").as[String] shouldBe s"${YearMonth.now().minusYears(3).getYear}-04-30"
//      (response.json \ "nextPaymentMonthStartDate")
//        .as[String] shouldBe s"${YearMonth.now().minusYears(3).getYear}-05-01"
//
//      (response.json \ "accountHolderName").as[String]  shouldBe "Testfore Testsur"
//      (response.json \ "accountHolderEmail").as[String] shouldBe "testemail@example.com"
//
//      val firstBonusTermJson = (response.json \ "bonusTerms")(0)
//      shouldBeBigDecimal(firstBonusTermJson \ "bonusEstimate", BigDecimal("90.99"))
//      shouldBeBigDecimal(firstBonusTermJson \ "bonusPaid", BigDecimal("90.99"))
//      (firstBonusTermJson \ "endDate").as[String] shouldBe s"${YearMonth.now().minusYears(2).getYear}-12-31"
//      (firstBonusTermJson \ "bonusPaidOnOrAfterDate")
//        .as[String] shouldBe s"${YearMonth.now().minusYears(1).getYear}-01-01"
//      (firstBonusTermJson \ "bonusPaidByDate")
//        .as[String]                                                         shouldBe s"${YearMonth.now().minusYears(1).getYear}-01-01"
//      (firstBonusTermJson \ "balanceMustBeMoreThanForBonus").as[BigDecimal] shouldBe 0
//
//      val secondBonusTermJson = (response.json \ "bonusTerms")(1)
//      shouldBeBigDecimal(secondBonusTermJson \ "bonusEstimate", BigDecimal(12))
//      shouldBeBigDecimal(secondBonusTermJson \ "bonusPaid", BigDecimal(0))
//      (secondBonusTermJson \ "endDate").as[String] shouldBe s"${YearMonth.now() getYear}-12-31"
//      (secondBonusTermJson \ "bonusPaidOnOrAfterDate")
//        .as[String] shouldBe s"${YearMonth.now().plusYears(1).getYear}-01-01"
//      (secondBonusTermJson \ "bonusPaidByDate")
//        .as[String]                                                          shouldBe s"${YearMonth.now().plusYears(1).getYear}-01-01"
//      (secondBonusTermJson \ "balanceMustBeMoreThanForBonus").as[BigDecimal] shouldBe BigDecimal("181.98")
//
//      (response.json \ "currentBonusTerm").as[String]   shouldBe "First"
//      (response.json \ "highestBalance").as[BigDecimal] shouldBe BigDecimal("181.98")
//    }
//
//    "respond with 200 and accountHolderEmail omitted when no email address are return from help to save" in {
//      loggedInAndEnrolled(nino)
//      HelpToSaveStub.accountExistsWithNoEmail(nino)
//
//      val response: WSResponse = await(requestWithAuthHeaders(s"/savings-account/$nino?journeyId=$journeyId").get())
//
//      response.status shouldBe 200
//
//      (response.json \ "number").as[String]          shouldBe "1000000000001"
//      (response.json \ "openedYearMonth").as[String] shouldBe "2018-01"
//      (response.json \ "isClosed").as[Boolean]       shouldBe false
//      shouldBeBigDecimal(response.json \ "balance", BigDecimal("123.45"))
//      shouldBeBigDecimal(response.json \ "paidInThisMonth", BigDecimal("27.88"))
//      shouldBeBigDecimal(response.json \ "canPayInThisMonth", BigDecimal("22.12"))
//      shouldBeBigDecimal(response.json \ "maximumPaidInThisMonth", BigDecimal(50))
//      (response.json \ "thisMonthEndDate").as[String]          shouldBe "2018-04-30"
//      (response.json \ "nextPaymentMonthStartDate").as[String] shouldBe "2018-05-01"
//
//      (response.json \ "accountHolderName").as[String] shouldBe "Testfore Testsur"
//      response.json.as[JsObject].keys                  should not contain "accountHolderEmail"
//
//      val firstBonusTermJson = (response.json \ "bonusTerms")(0)
//      shouldBeBigDecimal(firstBonusTermJson \ "bonusEstimate", BigDecimal("90.99"))
//      shouldBeBigDecimal(firstBonusTermJson \ "bonusPaid", BigDecimal("90.99"))
//      (firstBonusTermJson \ "endDate").as[String]                           shouldBe "2019-12-31"
//      (firstBonusTermJson \ "bonusPaidOnOrAfterDate").as[String]            shouldBe "2020-01-01"
//      (firstBonusTermJson \ "bonusPaidByDate").as[String]                   shouldBe "2020-01-01"
//      (firstBonusTermJson \ "balanceMustBeMoreThanForBonus").as[BigDecimal] shouldBe 0
//
//      val secondBonusTermJson = (response.json \ "bonusTerms")(1)
//      shouldBeBigDecimal(secondBonusTermJson \ "bonusEstimate", BigDecimal(12))
//      shouldBeBigDecimal(secondBonusTermJson \ "bonusPaid", BigDecimal(0))
//      (secondBonusTermJson \ "endDate").as[String]                           shouldBe "2021-12-31"
//      (secondBonusTermJson \ "bonusPaidOnOrAfterDate").as[String]            shouldBe "2022-01-01"
//      (secondBonusTermJson \ "bonusPaidByDate").as[String]                   shouldBe "2022-01-01"
//      (secondBonusTermJson \ "balanceMustBeMoreThanForBonus").as[BigDecimal] shouldBe BigDecimal("181.98")
//
//      (response.json \ "currentBonusTerm").as[String] shouldBe "First"
//    }
//
//    "respond with 404 and account not found when user is not enrolled" in {
//      ShutteringStub.stubForShutteringDisabled()
//      AuthStub.userIsLoggedIn(nino)
//      HelpToSaveStub.currentUserIsNotEnrolled()
//
//      val response: WSResponse = await(requestWithAuthHeaders(s"/savings-account/$nino?journeyId=$journeyId").get())
//
//      response.status shouldBe 404
//
//      (response.json \ "code").as[String]    shouldBe "ACCOUNT_NOT_FOUND"
//      (response.json \ "message").as[String] shouldBe "No Help to Save account exists for the specified NINO"
//
//      HelpToSaveStub.accountShouldNotHaveBeenCalled(nino)
//    }
//
//    "respond with 500 with general error message body when get account fails" in {
//      loggedInAndEnrolled(nino)
//      HelpToSaveStub.accountReturnsInternalServerError(nino)
//
//      val response: WSResponse = await(requestWithAuthHeaders(s"/savings-account/$nino?journeyId=$journeyId").get())
//
//      response.status shouldBe 500
//
//      (response.json \ "code").as[String] shouldBe "GENERAL"
//    }
//
//    "respond with 500 with general error message body when get account returns JSON that doesn't conform to the schema" in {
//      loggedInAndEnrolled(nino)
//      HelpToSaveStub.accountReturnsInvalidJson(nino)
//
//      val response: WSResponse = await(requestWithAuthHeaders(s"/savings-account/$nino?journeyId=$journeyId").get())
//
//      response.status shouldBe 500
//
//      (response.json \ "code").as[String] shouldBe "GENERAL"
//    }
//
//    "respond with 500 with general error message body when get account returns badly formed JSON" in {
//      loggedInAndEnrolled(nino)
//      HelpToSaveStub.accountReturnsBadlyFormedJson(nino)
//
//      val response: WSResponse = await(requestWithAuthHeaders(s"/savings-account/$nino?journeyId=$journeyId").get())
//
//      response.status shouldBe 500
//
//      (response.json \ "code").as[String] shouldBe "GENERAL"
//    }
//
//    "include account closure fields when account is closed" in {
//      loggedInAndEnrolled(nino)
//      HelpToSaveStub.closedAccountExists(nino)
//
//      val response: WSResponse = await(requestWithAuthHeaders(s"/savings-account/$nino?journeyId=$journeyId").get())
//      response.status shouldBe 200
//
//      (response.json \ "number").as[String]          shouldBe "1000000000002"
//      (response.json \ "openedYearMonth").as[String] shouldBe "2018-03"
//
//      (response.json \ "isClosed").as[Boolean]   shouldBe true
//      (response.json \ "closureDate").as[String] shouldBe "2018-04-09"
//      shouldBeBigDecimal(response.json \ "closingBalance", BigDecimal(10))
//
//      shouldBeBigDecimal(response.json \ "balance", BigDecimal(0))
//      shouldBeBigDecimal(response.json \ "paidInThisMonth", BigDecimal(0))
//      shouldBeBigDecimal(response.json \ "canPayInThisMonth", BigDecimal(50))
//      shouldBeBigDecimal(response.json \ "maximumPaidInThisMonth", BigDecimal(50))
//      (response.json \ "thisMonthEndDate").as[String]          shouldBe "2018-04-30"
//      (response.json \ "nextPaymentMonthStartDate").as[String] shouldBe "2018-05-01"
//
//      val firstBonusTermJson = (response.json \ "bonusTerms")(0)
//      shouldBeBigDecimal(firstBonusTermJson \ "bonusEstimate", BigDecimal("7.50"))
//      shouldBeBigDecimal(firstBonusTermJson \ "bonusPaid", BigDecimal(0))
//      (firstBonusTermJson \ "endDate").as[String]                           shouldBe "2020-02-29"
//      (firstBonusTermJson \ "bonusPaidOnOrAfterDate").as[String]            shouldBe "2020-03-01"
//      (firstBonusTermJson \ "bonusPaidByDate").as[String]                   shouldBe "2020-03-01"
//      (firstBonusTermJson \ "balanceMustBeMoreThanForBonus").as[BigDecimal] shouldBe 0
//
//      val secondBonusTermJson = (response.json \ "bonusTerms")(1)
//      shouldBeBigDecimal(secondBonusTermJson \ "bonusEstimate", BigDecimal(0))
//      shouldBeBigDecimal(secondBonusTermJson \ "bonusPaid", BigDecimal(0))
//      (secondBonusTermJson \ "endDate").as[String]                           shouldBe "2022-02-28"
//      (secondBonusTermJson \ "bonusPaidOnOrAfterDate").as[String]            shouldBe "2022-03-01"
//      (secondBonusTermJson \ "bonusPaidByDate").as[String]                   shouldBe "2022-03-01"
//      (secondBonusTermJson \ "balanceMustBeMoreThanForBonus").as[BigDecimal] shouldBe BigDecimal("15.00")
//
//      (response.json \ "currentBonusTerm").as[String] shouldBe "First"
//    }
//
//    "include account payments blocked field when account is enrolled but blocked" in {
//      loggedInAndEnrolled(nino)
//      HelpToSaveStub.paymentsBlockedAccountExists(nino)
//
//      val response: WSResponse = await(requestWithAuthHeaders(s"/savings-account/$nino?journeyId=$journeyId").get())
//      response.status shouldBe 200
//
//      (response.json \ "blocked" \ "payments").as[Boolean]    shouldBe true
//      (response.json \ "blocked" \ "withdrawals").as[Boolean] shouldBe false
//      (response.json \ "blocked" \ "bonuses").as[Boolean]     shouldBe false
//    }
//
//    "include account withdrawals blocked field when account is enrolled but blocked" in {
//      loggedInAndEnrolled(nino)
//      HelpToSaveStub.withdrawalsBlockedAccountExists(nino)
//
//      val response: WSResponse = await(requestWithAuthHeaders(s"/savings-account/$nino?journeyId=$journeyId").get())
//      response.status shouldBe 200
//
//      (response.json \ "blocked" \ "payments").as[Boolean]    shouldBe false
//      (response.json \ "blocked" \ "withdrawals").as[Boolean] shouldBe true
//      (response.json \ "blocked" \ "bonuses").as[Boolean]     shouldBe false
//    }
//
//    "include account bonuses blocked field when account is enrolled but blocked" in {
//      loggedInAndEnrolled(nino)
//      HelpToSaveStub.bonusesBlockedAccountExists(nino)
//
//      val response: WSResponse = await(requestWithAuthHeaders(s"/savings-account/$nino?journeyId=$journeyId").get())
//      response.status shouldBe 200
//
//      (response.json \ "blocked" \ "payments").as[Boolean]    shouldBe false
//      (response.json \ "blocked" \ "withdrawals").as[Boolean] shouldBe false
//      (response.json \ "blocked" \ "bonuses").as[Boolean]     shouldBe true
//    }
//
//    "return 401 when the user is not logged in" in {
//      ShutteringStub.stubForShutteringDisabled()
//      AuthStub.userIsNotLoggedIn()
//      val response: WSResponse = await(requestWithAuthHeaders(s"/savings-account/$nino?journeyId=$journeyId").get())
//      response.status shouldBe 401
//      response.body   shouldBe "Authorisation failure [Bearer token not supplied]"
//    }
//
//    "return 403 Forbidden when the user is logged in with an insufficient confidence level" in {
//      ShutteringStub.stubForShutteringDisabled()
//      AuthStub.userIsLoggedInWithInsufficientConfidenceLevel()
//      val response: WSResponse = await(requestWithAuthHeaders(s"/savings-account/$nino?journeyId=$journeyId").get())
//      response.status shouldBe 403
//      response.body   shouldBe "Authorisation failure [Insufficient ConfidenceLevel]"
//    }
//
//    "return 400 when no journeyId is supplied" in {
//      AuthStub.userIsNotLoggedIn()
//      val response: WSResponse = await(requestWithAuthHeaders(s"/savings-account/$nino").get())
//      response.status shouldBe 400
//    }
//
//    "return 400 when invalid journeyId is supplied" in {
//      AuthStub.userIsNotLoggedIn()
//      val response: WSResponse =
//        await(requestWithAuthHeaders(s"/savings-account/$nino?journeyId=ThisIsAnInvalidJourneyId").get())
//      response.status shouldBe 400
//    }
//
//    "return 400 when invalid NINO supplied" in {
//      AuthStub.userIsNotLoggedIn()
//      val response: WSResponse =
//        await(requestWithAuthHeaders(s"/savings-account/AA123123123?journeyId=$journeyId").get())
//      response.status shouldBe 400
//    }
//  }
//}
