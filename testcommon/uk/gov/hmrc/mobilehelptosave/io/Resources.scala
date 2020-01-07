/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.mobilehelptosave.io

import java.io.InputStream

object Resources {
  def withResource[R](resourceName: String, clazz: Class[_] = getClass)(f: InputStream => R): R = {
    val inputStreamIfExists = Option(clazz.getResourceAsStream(resourceName))
    inputStreamIfExists.map { inputStream =>
      try {
        f(inputStream)
      }
      finally {
        inputStream.close()
      }
    }.getOrElse(sys.error(s"Could not find resource $resourceName"))
  }
}
