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
package nl.knaw.dans.easy.managedeposit

import java.nio.file.{ Files, Path, Paths }
import java.text.SimpleDateFormat
import java.util.Calendar

import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.commons.configuration.PropertiesConfiguration
import org.apache.commons.csv.CSVFormat
import org.apache.commons.io.FileUtils
import org.joda.time.{ DateTime, DateTimeZone, Duration }
import resource.managed

import scala.collection.JavaConverters._
import scala.language.postfixOps
import scala.math.Ordering.{ Long => LongComparator }
import scala.util.Try
import scala.xml.{ NodeSeq, XML }

class EasyManageDepositApp(configuration: Configuration) extends DebugEnhancedLogging {
  private val KB = 1024L
  private val MB = 1024L * KB
  private val GB = 1024L * MB
  private val TB = 1024L * GB

  private val sword2DepositsDir = Paths.get(configuration.properties.getString("easy-sword2"))
  private val ingestFlowInbox = Paths.get(configuration.properties.getString("easy-ingest-flow-inbox"))

  private val end = DateTime.now(DateTimeZone.UTC)

  private def collectDataFromDepositsDir(depositsDir: Path, filterOnDepositor: Option[DepositorId], filterOnAge: Option[Age]): Deposits = {
    depositsDir.list(collectDataFromDepositsDir(filterOnDepositor, filterOnAge))
  }

  def deleteDepositFromDepositsDir(depositsDir: Path, filterOnDepositor: Option[DepositorId], age: Int, state: String, onlyData: Boolean): Unit = {
    depositsDir.list(deleteDepositFromDepositsDir(filterOnDepositor, age, state, onlyData))
  }

  def retryStalledDeposit(depositsDir: Path, filterOnDepositor: Option[DepositorId]): Unit = {
    depositsDir.list(retryStalledDeposit(filterOnDepositor))
  }

  private def readDepositProperties(depositDir: Path): PropertiesConfiguration = {
    new PropertiesConfiguration() {
      setDelimiterParsingDisabled(true)
      load(depositDir.resolve("deposit.properties").toFile)
    }
  }

  case class NotReadableException(path: Path, cause: Throwable = null)
    extends Exception(s"""cannot read $path""", cause)


  private def collectDataFromDepositsDir(filterOnDepositor: Option[DepositorId], filterOnAge: Option[Age])(deposits: List[Path]): Deposits = {
    trace(filterOnDepositor)
    deposits.filter(Files.isDirectory(_))
      .flatMap { depositDirPath =>

        val depositPropertiesFilePath = depositDirPath.resolve("deposit.properties")
        debug(s"Getting info from $depositDirPath")

        if (!Files.isReadable(depositDirPath)) {
          logger.error(s"ERROR: cannot read $depositDirPath")
          throw new NotReadableException(depositDirPath)
        }
        else if (!Files.isReadable(depositPropertiesFilePath)) {
          logger.error(s"ERROR: cannot read $depositPropertiesFilePath")
          throw new NotReadableException(depositPropertiesFilePath)
        }

        debug(s"Getting info from $depositDirPath")
        val depositProperties = readDepositProperties(depositDirPath)
        val depositId = depositProperties.getString("bag-store.bag-id")
        val depositorId = depositProperties.getString("depositor.userId")

        lazy val lastModified: Option[DateTime] = getLastModifiedTimestamp(depositDirPath)

        // forall returns true for the empty set, see https://en.wikipedia.org/wiki/Vacuous_truth
        val hasDepositor = filterOnDepositor.forall(depositorId ==)
        lazy val shouldReport = filterOnAge.forall(age => lastModified.forall(mod => Duration.millis(DateTime.now(mod.getZone).getMillis - mod.getMillis).getStandardDays <= age))

        if (hasDepositor && shouldReport) Some {
          Deposit(
            depositId = depositId,
            doi = getDoi(depositProperties, depositDirPath),
            depositorId,
            state = depositProperties.getString("state.label"),
            description = depositProperties.getString("state.description"),
            creationTimestamp = Option(depositProperties.getString("creation.timestamp")).getOrElse("n/a"),
            depositDirPath.list(_.count(_.getFileName.toString.matches("""^.*\.zip\.\d+$"""))),
            storageSpace = FileUtils.sizeOfDirectory(depositDirPath.toFile),
            lastModified = lastModified.map(_.toString(dateTimeFormatter)).getOrElse("n/a")
          )
        }
        else None
      }
  }

  def deleteDepositFromDepositsDir(filterOnDepositor: Option[DepositorId], age: Int, state: String, onlyData: Boolean)(list: List[Path]): Unit = {
    list.filter(Files.isDirectory(_))
      .foreach { depositDirPath =>
        val depositPropertiesFilePath = depositDirPath.resolve("deposit.properties")
        if (!Files.isReadable(depositDirPath)) {
          logger.error(s"ERROR: cannot read $depositDirPath")
          throw new NotReadableException(depositDirPath)
        }
        else if (!Files.isReadable(depositPropertiesFilePath)) {
          logger.error(s"ERROR: cannot read $depositPropertiesFilePath")
          throw new NotReadableException(depositPropertiesFilePath)
        }

        val depositProperties = readDepositProperties(depositDirPath)
        val depositorId = depositProperties.getString("depositor.userId")
        val creationTime = depositProperties.getString("creation.timestamp")
        val depositState = depositProperties.getString("state.label")
        val start = new DateTime(creationTime)
        val duration = new Duration(start, end)
        val depositAge = duration.getStandardDays

        // forall returns true for the empty set, see https://en.wikipedia.org/wiki/Vacuous_truth
        if (filterOnDepositor.forall(depositorId ==) && depositAge > age && depositState == state) {
          if (onlyData) {
            for (file <- depositDirPath.toFile.listFiles(); if file.getName != "deposit.properties") {
              //TODO Is the following readability check required here ??
                if (!Files.isReadable(file.toPath)) {
                  val filePath = file.toPath
                  logger.error(s"ERROR: cannot read $filePath")
                  throw new NotReadableException(filePath)
                }
              logger.info(s"DELETE data from deposit for $depositorId from $depositState $depositDirPath")
              FileUtils.deleteDirectory(file)
            }
          }
          else {
            logger.info(s"DELETE deposit for $depositorId from $depositState $depositDirPath")
            FileUtils.deleteDirectory(depositDirPath.toFile)
          }
        }
      }
  }

  def retryStalledDeposit(filterOnDepositor: Option[DepositorId])(list: List[Path]): Unit = {
    list.filter(Files.isDirectory(_))
      .foreach { depositDirPath =>
        val depositPropertiesFilePath = depositDirPath.resolve("deposit.properties")
        if (!Files.isReadable(depositDirPath)) {
          logger.error(s"ERROR: cannot read $depositDirPath")
          throw new NotReadableException(depositDirPath)
        }
        else if (!Files.isReadable(depositPropertiesFilePath)) {
          logger.error(s"ERROR: cannot read $depositPropertiesFilePath")
          throw new NotReadableException(depositPropertiesFilePath)
        }
        val depositProperties = readDepositProperties(depositDirPath)
        val depositorId = depositProperties.getString("depositor.userId")
        val depositState = depositProperties.getString("state.label")

        // forall returns true for the empty set, see https://en.wikipedia.org/wiki/Vacuous_truth
        if (filterOnDepositor.forall(depositorId ==)) {
          if (depositState == "STALLED") {
            logger.info(s"RESET to SUBMITTED for $depositorId on $depositDirPath")
            depositProperties.setProperty("state.label", "SUBMITTED")
            depositProperties.save()
          }
        }
      }
  }

  private def getLastModifiedTimestamp(depositDirPath: Path): Option[DateTime] = {
    if (!Files.isReadable(depositDirPath)) {
      logger.error(s"ERROR: cannot read $depositDirPath")
      throw new NotReadableException(depositDirPath)
    }
    managed(Files.list(depositDirPath)).acquireAndGet { files =>
      files.forEach(file => if (!Files.isReadable(file.toRealPath())) {
        val pathOfAZipFileOrPropFileInDepositDirPath = file.toRealPath()
        logger.error(s"ERROR: cannot read $pathOfAZipFileOrPropFileInDepositDirPath")
        throw new NotReadableException(pathOfAZipFileOrPropFileInDepositDirPath)
      }
      )
    }
    managed(Files.list(depositDirPath)).acquireAndGet { files =>
      files.map[Long](Files.getLastModifiedTime(_).toInstant.toEpochMilli)
        .max(LongComparator)
        .map[DateTime](millis => new DateTime(millis, DateTimeZone.UTC))
        .toOption
    }
  }

  private def getDoi(depositProperties: PropertiesConfiguration, depositDirPath: Path): Option[String] = {
    if (!Files.isReadable(depositDirPath)) {
      logger.error(s"ERROR: cannot read $depositDirPath")
      throw new NotReadableException(depositDirPath)
    }
    managed(Files.list(depositDirPath)).acquireAndGet { files =>
      files.forEach(file => if (!Files.isReadable(file.toRealPath())) {
        val pathOfAZipFileOrPropFileInDepositDirPath = file.toRealPath()
        logger.error(s"ERROR: cannot read $pathOfAZipFileOrPropFileInDepositDirPath")
        throw new NotReadableException(pathOfAZipFileOrPropFileInDepositDirPath)
      }
      )
    }
    managed(Files.list(depositDirPath)).acquireAndGet { files =>
      files.iterator().asScala.toStream
        .collectFirst { case bagDir if Files.isDirectory(bagDir) =>
          if (!Files.isReadable(bagDir)) {
            logger.error(s"ERROR: cannot read $bagDir")
            throw new NotReadableException(bagDir)
          }
          if (Files.isReadable(bagDir)) {
            if (!Files.isReadable(bagDir.resolve("metadata/dataset.xml"))) {
              val pathOfDatasetXml = bagDir.resolve("metadata/dataset.xml")
              logger.error(s"ERROR: cannot read $pathOfDatasetXml")
              throw new NotReadableException(pathOfDatasetXml)
            }
          }
        }
    }
    Option(depositProperties.getString("identifier.doi")).orElse {
      managed(Files.list(depositDirPath)).acquireAndGet { files =>
        files.iterator().asScala.toStream
          .collectFirst { case bagDir if Files.isDirectory(bagDir) => bagDir.resolve("metadata/dataset.xml") }
          .flatMap {
            case datasetXml if Files.exists(datasetXml) => Try {
              val docElement = XML.loadFile(datasetXml.toFile)
              findDoi(docElement \\ "dcmiMetadata" \\ "identifier")
            }.getOrElse(None)
            case _ => None
          }
      }
    }
  }

  private def findDoi(identifiers: NodeSeq): Option[String] = {
    identifiers.find { id =>
      id.attribute(XML_NAMESPACE_XSI, "type").exists {
        case Seq(n) =>
          n.text.split(':') match {
            case Array(pre, suffix) => id.getNamespace(pre) == XML_NAMESPACE_ID_TYPE && suffix == "DOI"
            case _ => false
          }
      }
    }.map(_.text)
  }

  private def outputSummary(deposits: Deposits, depositor: Option[DepositorId] = None): Unit = {
    val selectedDeposits = depositor.map(d => deposits.filter(_.depositor == d)).getOrElse(deposits)
    val draft = selectedDeposits.filter(_.state == "DRAFT").toList
    val invalid = selectedDeposits.filter(_.state == "INVALID").toList
    val finalizing = selectedDeposits.filter(_.state == "FINALIZING").toList
    val submitted = selectedDeposits.filter(_.state == "SUBMITTED").toList
    val archived = selectedDeposits.filter(_.state == "ARCHIVED").toList
    val rejected = selectedDeposits.filter(_.state == "REJECTED").toList
    val failed = selectedDeposits.filter(_.state == "FAILED").toList

    val now = Calendar.getInstance().getTime
    val format = new SimpleDateFormat("yyyy-MM-dd")
    val currentTime = format.format(now)

    println("Grand totals:")
    println("-------------")
    println(s"Timestamp          : $currentTime")
    println(f"Number of deposits : ${ selectedDeposits.size }%10d")
    println(s"Total space        : ${ formatStorageSize(selectedDeposits.map(_.storageSpace).sum) }")
    println()
    println("Per state:")
    println("----------")
    println(formatCountAndSize(draft, "DRAFT"))
    println(formatCountAndSize(invalid, "INVALID"))
    println(formatCountAndSize(finalizing, "FINALIZING"))
    println(formatCountAndSize(submitted, "SUBMITTED"))
    println(formatCountAndSize(archived, "ARCHIVED"))
    println(formatCountAndSize(rejected, "REJECTED"))
    println(formatCountAndSize(failed, "FAILED"))
    println()
  }

  private def outputFullReport(deposits: Deposits): Unit = {
    val csvFormat: CSVFormat = CSVFormat.RFC4180
      .withHeader("DEPOSITOR", "DEPOSIT_ID", "DEPOSIT_STATE", "DOI", "DEPOSIT_CREATION_TIMESTAMP",
        "DEPOSIT_UPDATE_TIMESTAMP", "DESCRIPTION", "NBR_OF_CONTINUED_DEPOSITS", "STORAGE_IN_BYTES")
      .withDelimiter(',')
      .withRecordSeparator('\n')
    val printer = csvFormat.print(Console.out)
    deposits.sortBy(_.creationTimestamp) foreach { deposit =>
      printer.printRecord(
        deposit.depositor,
        deposit.depositId,
        deposit.state,
        deposit.doi.getOrElse("n/a"),
        deposit.creationTimestamp,
        deposit.lastModified,
        deposit.description,
        deposit.numberOfContinuedDeposits.toString,
        deposit.storageSpace.toString)
    }
  }

  private def formatStorageSize(nBytes: Long): String = {
    def formatSize(unitSize: Long, unit: String): String = {
      f"${ nBytes / unitSize.toFloat }%8.1f $unit"
    }

    if (nBytes > 1.1 * TB) formatSize(TB, "T")
    else if (nBytes > 1.1 * GB) formatSize(GB, "G")
    else if (nBytes > 1.1 * MB) formatSize(MB, "M")
    else if (nBytes > 1.1 * KB) formatSize(KB, "K")
    else formatSize(1, "B")
  }

  private def formatCountAndSize(deposits: List[Deposit], filterOnState: String): String = {
    f"${ filterOnState }%-10s : ${ deposits.size }%5d (${ formatStorageSize(deposits.map(_.storageSpace).sum) })"
  }

  def summary(depositor: Option[DepositorId], age: Option[Age]): Try[String] = Try {
    val sword2Deposits = collectDataFromDepositsDir(sword2DepositsDir, depositor, age)
    val ingestFlowDeposits = collectDataFromDepositsDir(ingestFlowInbox, depositor, age)
    outputSummary(sword2Deposits ++ ingestFlowDeposits, depositor)
    "End of summary report."
  }

  def createFullReport(depositor: Option[DepositorId], age: Option[Age]): Try[String] = Try {
    val sword2Deposits = collectDataFromDepositsDir(sword2DepositsDir, depositor, age)
    val ingestFlowDeposits = collectDataFromDepositsDir(ingestFlowInbox, depositor, age)
    outputFullReport(sword2Deposits ++ ingestFlowDeposits)
    "End of full report."
  }

  def retryDepositor(depositor: Option[DepositorId]): Try[String] = Try {
    retryStalledDeposit(ingestFlowInbox, depositor)
    "STALLED states were replaced by SUBMITTED states."
  }

  def cleanDepositor(depositor: Option[DepositorId], age: Int, state: String, onlyData: Boolean): Try[String] = Try {
    deleteDepositFromDepositsDir(sword2DepositsDir, depositor, age, state, onlyData)
    deleteDepositFromDepositsDir(ingestFlowInbox, depositor, age, state, onlyData)
    "Execution of clean: success "
  }
}
