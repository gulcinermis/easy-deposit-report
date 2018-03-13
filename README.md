easy-manage-deposit
===========
[![Build Status](https://travis-ci.org/DANS-KNAW/easy-manage-deposit.png?branch=master)](https://travis-ci.org/DANS-KNAW/easy-manage-deposit)


SYNOPSIS
--------
   
     easy-manage-deposit report full [<depositor>]
     easy-manage-deposit report summary [<depositor>]
     easy-manage-deposit clean --data-only --state [<state>] --keep [<n>][<depositor>]
     easy-manage-deposit retry [<depositor>]
     
         
ARGUMENTS
--------
   
     Options:
                --help      Show help message
                --version   Show version of this program
        
          Subcommand: report
                  --help   Show help message
          
            Subcommand: report full - creates a full report for depositor(optional)
                  --help   Show help message
          
             trailing arguments:
              depositor (not required)
            ---
          
            Subcommand: report summary - creates a summary report for depositor(optional)
                  --help   Show help message
          
             trailing arguments:
              depositor (not required)
            ---
            Subcommand: clean
              -d, --data-only      If specified, the deposit.properties and the container
                                   file of the deposit are not deleted
              -k, --keep  <arg>    The deposits whose ages are strictly greater than the
                                   argument n (days) are deleted. An age argument of n=0
                                   days corresponds to 0<=n<1. The default case is set to
                                   n=-1, so that the deposits that are younger than 1 day
                                   are not skipped in the default case. (default = -1)
              -s, --state  <arg>   The deposits with the specified state argument are
                                   deleted (default = DRAFT)
                  --help           Show help message
          
             trailing arguments:
              depositor (not required)
            ---
          
            Subcommand: retry
                  --help   Show help message
          
             trailing arguments:
              depositor (not required)
     ---
    
     
DESCRIPTION
-----------

Manages the deposits in the deposit area.
     
EXAMPLES
--------

     easy-manage-deposit report full someUserId
     easy-manage-deposit report summary someUserId
     easy-manage-deposit report full -a 0 someUserId
     easy-manage-deposit report summary --age 2 someUserId
     easy-manage-deposit clean someUserId
     easy-manage-deposit clean --data-only --state <state> --keep <n> someUserId
     easy-manage-deposit retry someUserId


INSTALLATION AND CONFIGURATION
------------------------------


1. Unzip the tarball to a directory of your choice, typically `/usr/local/`
2. A new directory called easy-manage-deposit-<version> will be created
3. Add the command script to your `PATH` environment variable by creating a symbolic link to it from a directory that is
   on the path, e.g. 
   
        ln -s /usr/local/easy-manage-deposit-<version>/bin/easy-manage-deposit /usr/bin



General configuration settings can be set in `cfg/application.properties` and logging can be configured
in `cfg/logback.xml`. The available settings are explained in comments in aforementioned files.


BUILDING FROM SOURCE
--------------------

Prerequisites:

* Java 8 or higher
* Maven 3.3.3 or higher

Steps:

        git clone https://github.com/DANS-KNAW/easy-manage-deposit.git
        cd easy-manage-deposit
        mvn install
