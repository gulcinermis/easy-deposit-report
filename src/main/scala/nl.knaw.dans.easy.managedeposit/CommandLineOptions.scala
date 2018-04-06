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

import org.rogach.scallop.{ ScallopConf, ScallopOption, Subcommand }

import scala.language.{ postfixOps, reflectiveCalls }

class CommandLineOptions(args: Array[String], configuration: Configuration) extends ScallopConf(args) {
  appendDefaultToDescription = true
  editBuilder(_.setHelpWidth(110))
  printedName = "easy-manage-deposit"
  private val SUBCOMMAND_SEPARATOR = "---\n"
  val description: String = s"""Manages the deposits in the deposit area."""
  val synopsis: String =
    s"""
       |  $printedName report full [-a, --age <n>] [<depositor>]
       |  $printedName report summary [-a, --age <n>] [<depositor>]
       |  $printedName clean [-d, --data-only] [-s, --state <state>] [-k, --keep <n>] [<depositor>]
       |  $printedName retry [<depositor>]
     """.stripMargin
  version(s"$printedName v${ configuration.version }")
  banner(
    s"""
       |  $description
       |
       |Usage:
       |
       |$synopsis
       |
       |Options:
       |
       |""".stripMargin)
  val reportCmd = new Subcommand("report") {

    val fullCmd = new Subcommand("full") {
      val depositor: ScallopOption[DepositorId] = trailArg("depositor", required = false)
      val age: ScallopOption[Age] = opt[Age](name = "age", short = 'a', validate = 0 <=,
        descr = "Only report on the deposits that are less than n days old. An age argument of n=0 days corresponds to 0<=n<1. If this argument is not provided, all deposits will be reported on.")
      descr("creates a full report for depositor(optional)")
      footer(SUBCOMMAND_SEPARATOR)
    }
    addSubcommand(fullCmd)

    val summaryCmd = new Subcommand("summary") {
      val depositor: ScallopOption[DepositorId] = trailArg("depositor", required = false)
      val age: ScallopOption[Age] = opt[Age](name = "age", short = 'a', validate = 0 <=,
        descr = "Only report on the deposits that are less than n days old. An age argument of n=0 days corresponds to 0<=n<1. If this argument is not provided, all deposits will be reported on.")
      descr("creates a summary report for depositor(optional)")
      footer(SUBCOMMAND_SEPARATOR)
    }
    addSubcommand(summaryCmd)
  }
  addSubcommand(reportCmd)

  val cleanCmd = new Subcommand("clean") {
    val depositor: ScallopOption[DepositorId] = trailArg("depositor", required = false)
    val dataOnly: ScallopOption[Boolean] = opt[Boolean](default = Some(false), descr = "If specified, the deposit.properties and the container file of the deposit are not deleted")
    val state: ScallopOption[String] = opt[String](default = Some("DRAFT"), descr = "The deposits with the specified state argument are deleted")
    val keep: ScallopOption[Int] = opt[Int](default = Some(-1), validate = -1 <=, descr = "The deposits whose ages are strictly greater than the argument n (days) are deleted. An age argument of n=0 days corresponds to 0<=n<1. The default case is set to n=-1, so that the deposits that are younger than 1 day are not skipped in the default case.")
    descr("removes deposit with specified state")
    footer(SUBCOMMAND_SEPARATOR)
  }
  addSubcommand(cleanCmd)

  val retryCmd = new Subcommand("retry") {
    val depositor: ScallopOption[DepositorId] = trailArg("depositor", required = false)
    footer(SUBCOMMAND_SEPARATOR)
  }
  addSubcommand(retryCmd)


  footer("")
  verify()
}
