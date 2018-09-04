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

package uk.gov.hmrc.mobilehelptosave.controllers

import play.api.mvc.{Request, Result, Results}
import uk.gov.hmrc.domain.Nino

import scala.concurrent.Future

class AlwaysAuthorisedWithIds(nino: Nino) extends AuthorisedWithIds {
  override protected def refine[A](request: Request[A]): Future[Either[Result, RequestWithIds[A]]] =
    Future successful Right(new RequestWithIds(nino, request))
}

object NeverAuthorisedWithIds extends AuthorisedWithIds with Results {
  override protected def refine[A](request: Request[A]): Future[Either[Result, RequestWithIds[A]]] =
    Future successful Left(Forbidden)
}

object ShouldNotBeCalledAuthorisedWithIds extends AuthorisedWithIds with Results {
  override protected def refine[A](request: Request[A]): Future[Either[Result, RequestWithIds[A]]] =
    Future failed new RuntimeException("AuthorisedWithIds should not be called in this situation")
}
