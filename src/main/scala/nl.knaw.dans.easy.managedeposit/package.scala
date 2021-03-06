/**
 * Copyright (C) 2017 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy

import java.nio.file.{ Files, Path }

import org.joda.time.format.{ DateTimeFormatter, ISODateTimeFormat }
import resource._

import scala.collection.JavaConverters._

package object managedeposit {

  type DepositId = String
  type Deposits = Seq[Deposit]
  type DepositorId = String

  val XML_NAMESPACE_XSI = "http://www.w3.org/2001/XMLSchema-instance"
  val XML_NAMESPACE_ID_TYPE = "http://easy.dans.knaw.nl/schemas/vocab/identifier-type/"

  val dateTimeFormatter: DateTimeFormatter = ISODateTimeFormat.dateTime()

  implicit class PathExtensions(val path: Path) extends AnyVal {
    def list[T](f: List[Path] => T): T = {
      managed(Files.list(path)).acquireAndGet(stream => f(stream.iterator().asScala.toList))
    }
  }
}
