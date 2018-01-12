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

import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OneInstancePerTest, WordSpec}
import org.slf4j.Logger
import play.api.LoggerLike
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.connectors.NativeAppWidgetConnector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class SurveyServiceSpec extends
  WordSpec with Matchers with FutureAwaits with DefaultAwaitTimeout with
  MockFactory with OneInstancePerTest {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  // when https://github.com/paulbutcher/ScalaMock/issues/39 is fixed we will be able to simplify this code by mocking LoggerLike directly (instead of slf4j.Logger)
  private val slf4jLoggerStub = stub[Logger]
  (slf4jLoggerStub.isWarnEnabled: () => Boolean).when().returning(true)
  private val logger = new LoggerLike {
    override val logger: Logger = slf4jLoggerStub
  }

  "userWantsToBeContacted" should {

    "return false for users who haven't answered the survey question" in {
      val service = new SurveyServiceImpl(logger, fakeNativeAppWidgetConnector(Some(Seq.empty)))
      await(service.userWantsToBeContacted()) shouldBe Some(false)
    }

    "return true for users who have answered Yes to the survey question" in {
      val service = new SurveyServiceImpl(logger, fakeNativeAppWidgetConnector(Some(Seq("Yes"))))
      await(service.userWantsToBeContacted()) shouldBe Some(true)
    }

    "return false for users who have answered No to the survey question" in {
      val service = new SurveyServiceImpl(logger, fakeNativeAppWidgetConnector(Some(Seq("No"))))
      await(service.userWantsToBeContacted()) shouldBe Some(false)
    }

    "return true for users who have answered the survey more than once with a mixture of Yes and No answers" in {
      val service = new SurveyServiceImpl(logger, fakeNativeAppWidgetConnector(Some(Seq("No", "Yes", "No"))))
      await(service.userWantsToBeContacted()) shouldBe Some(true)
    }

    "return true for users who have answered yes (lowercase) to the survey question" in {
      val service = new SurveyServiceImpl(logger, fakeNativeAppWidgetConnector(Some(Seq("yes"))))
      await(service.userWantsToBeContacted()) shouldBe Some(true)
    }

    """log a warning for answers other than "Yes" or "No"""" in {
      val service = new SurveyServiceImpl(logger, fakeNativeAppWidgetConnector(Some(Seq("no", "other", "not yes"))))
      await(service.userWantsToBeContacted()) shouldBe Some(false)

      (slf4jLoggerStub.warn(_: String)) verify """Unknown survey answer "other" found"""
      (slf4jLoggerStub.warn(_: String)) verify """Unknown survey answer "not yes" found"""

    }

    """not log a warning for answers "Yes" or "No"""" in {
      val service = new SurveyServiceImpl(logger, fakeNativeAppWidgetConnector(Some(Seq("No", "no", "Yes", "yes"))))
      await(service.userWantsToBeContacted())

      (slf4jLoggerStub.warn(_: String)).verify(*).never
    }

    "return None when the survey answers cannot be retrieved" in {
      val service = new SurveyServiceImpl(logger, fakeNativeAppWidgetConnector(None))
      await(service.userWantsToBeContacted()) shouldBe None
    }

  }

  private def fakeNativeAppWidgetConnector(answersForHtsQ3: Option[Seq[String]]) = new NativeAppWidgetConnector {
    override def getAnswers(campaignId: String, questionKey: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Seq[String]]] =
      if (campaignId == "HELP_TO_SAVE_1" && questionKey == "question_3")
        Future successful answersForHtsQ3
      else
        Future successful Some(Seq.empty)
  }

}
