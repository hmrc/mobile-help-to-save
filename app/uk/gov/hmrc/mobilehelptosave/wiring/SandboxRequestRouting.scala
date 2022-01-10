/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.mobilehelptosave.wiring
import play.api.BuiltInComponents
import play.api.http.{DefaultHttpFilters, HttpRequestHandler}
import uk.gov.hmrc.api.sandbox.RoutingHttpRequestHandler

trait SandboxRequestRouting {
  self: BuiltInComponents =>

  override lazy val httpRequestHandler: HttpRequestHandler =
    new RoutingHttpRequestHandler(router,
                                  httpErrorHandler,
                                  httpConfiguration,
                                  new DefaultHttpFilters(httpFilters: _*),
                                  environment,
                                  configuration)
}
