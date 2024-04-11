package uk.gov.hmrc.mobilehelptosave.support

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.mobilehelptosave.TransactionTestData

trait BaseISpec
    extends AnyWordSpecLike
    with Matchers
    with TransactionTestData
    with FutureAwaits
    with DefaultAwaitTimeout
    with WireMockSupport
    with OneServerPerSuiteWsClient {}
