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

import javax.inject.{Inject, Singleton}

import cats.data.OptionT
import cats.instances.future._
import com.google.inject.ImplementedBy
import play.api.LoggerLike
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.connectors.NativeAppWidgetConnector

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SurveyServiceImpl])
trait SurveyService {
  def userWantsToBeContacted()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]]
}

@Singleton
class SurveyServiceImpl @Inject() (
  logger: LoggerLike,
  nativeAppWidgetConnector: NativeAppWidgetConnector
) extends SurveyService {

  // The campaign ID, question key and yes answer all need to match the values used by the Android and iOS apps.
  private val helpToSaveCampaignId = "HELP_TO_SAVE_1"
  private val wantsToBeContactedQuestionKey = "question_3"
  /**
    * The answer value used when the user answers yes to the above question.
    *
    * This needs to match the following values used by the native apps:
    * * uk.gov.hmrc.ptcalc.presentation.main.taxaccount.taxcredits.helptosave.HelpToSaveViewModelFactory.createQuestion3() on Android
    * * CampaignViewModel.yesAction() on iOS
    */
  private val yesAnswer = "Yes"
  private val noAnswer = "No"

  override def userWantsToBeContacted()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] =
    OptionT(nativeAppWidgetConnector.answers(helpToSaveCampaignId, wantsToBeContactedQuestionKey)).map { answers: Seq[String] =>
      answers
        .filterNot(answer => answer.equalsIgnoreCase(yesAnswer) || answer.equalsIgnoreCase(noAnswer))
        .foreach(answer => logger.warn(s"""Unknown survey answer "$answer" found"""))
      answers.exists(_.equalsIgnoreCase(yesAnswer))
    }.value
}
