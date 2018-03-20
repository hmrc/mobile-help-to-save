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

import java.time.LocalDate

import play.api.libs.json.{Format, Json}
import ai.x.play.json.Jsonx

case class NSIGetAccountByNinoResponse(version:                   String,
                                       correlationId:             Option[String],
                                       accountNumber:             String,
                                       availableWithdrawal:       String,
                                       accountBalance:            String,
                                       accountClosedFlag:         String,
                                       accountBlockingCode:       String,
                                       accountBlockingReasonCode: String,
                                       currentInvestmentMonth:    CurrentInvestmentMonth,
                                       clientForename:            String,
                                       clientSurname:             String,
                                       dateOfBirth:               LocalDate,
                                       addressLine1:              String,
                                       addressLine2:              String,
                                       addressLine3:              String,
                                       addressLine4:              String,
                                       addressLine5:              String,
                                       postCode:                  String,
                                       countryCode:               String,
                                       emailAddress:              String,
                                       commsPreference:           String,
                                       clientBlockingCode:        String,
                                       clientBlockingReasonCode:  String,
                                       clientCancellationStatus:  String,
                                       nbaAccountNumber:          String,
                                       nbaPayee:                  String,
                                       nbaRollNumber:             String,
                                       nbaSortCode:               String,
                                       terms:                     List[Term])

object NSIGetAccountByNinoResponse {

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Equals", "org.wartremover.warts.IsInstanceOf"))
  implicit val format: Format[NSIGetAccountByNinoResponse] = Jsonx.formatCaseClass[NSIGetAccountByNinoResponse]
}

case class CurrentInvestmentMonth(investmentRemaining: String, investmentLimit: String, endDate: LocalDate)

object CurrentInvestmentMonth {

  implicit val format: Format[CurrentInvestmentMonth] = Json.format[CurrentInvestmentMonth]
}

case class Term(termNumber: Int, startDate: LocalDate, endDate: LocalDate, maxBalance: String, bonusEstimate: String, bonusPaid: String)

object Term {

  implicit val format: Format[Term] = Json.format[Term]
}