#!/usr/bin/env bash
#
# Helper script to create full and summary reports and send them to a list of recipients.
#
# Usage: ./create-and-send-report.sh <host-name> <depositor-account> [<from-email>] <to-email> [<bcc-email>]
#
# Use - (dash) as depositor-account to generate a report for all the deposits.
#

EASY_HOST=$1
EASY_ACCOUNT=$2
FROM=$3
TO=$4
BCC=$5
TMPDIR=/tmp

if [ "$EASY_ACCOUNT" == "-" ]; then
    EASY_ACCOUNT=""
fi

DATE=$(date +%Y-%m-%d)
REPORT_SUMMARY=/tmp/report-summary-${EASY_ACCOUNT:-all}-$DATE.txt
REPORT_SUMMARY_24=/tmp/report-summary-${EASY_ACCOUNT:-all}-yesterday-$DATE.txt
REPORT_FULL=/tmp/report-full-${EASY_ACCOUNT:-all}-$DATE.csv
REPORT_FULL_24=/tmp/report-full-${EASY_ACCOUNT:-all}-yesterday-$DATE.csv


if [ "$FROM" == "" ]; then
    FROM_EMAIl=""
else
    FROM_EMAIL="-r $FROM"
fi

if [ "$BCC" == "" ]; then
    BCC_EMAILS=""
else
    BCC_EMAILS="-b $BCC"
fi

TO_EMAILS="$TO"

exit_if_failed() {
    local EXITSTATUS=$?
    if [ $EXITSTATUS != 0 ]; then
        echo "ERROR: $1, exit status = $EXITSTATUS"
        echo "Report generation FAILED. Contact the system administrator." |
        mail -s "FAILED: Report: status of EASY deposits (${EASY_ACCOUNT:-all depositors})" \
             $FROM_EMAIL $BCC_EMAILS $TO
        exit 1
    fi
    echo "OK"
}

echo -n "Creating summary report for ${EASY_ACCOUNT:-all depositors}..."
/opt/dans.knaw.nl/easy-manage-deposit/bin/easy-manage-deposit report summary $EASY_ACCOUNT > $REPORT_SUMMARY
exit_if_failed "summary report failed"

echo -n "Creating summary report from the last 24 hours for ${EASY_ACCOUNT:-all depositors}..."
/opt/dans.knaw.nl/easy-manage-deposit/bin/easy-manage-deposit report summary --age 0 $EASY_ACCOUNT > $REPORT_SUMMARY_24
exit_if_failed "summary report failed"

echo -n "Creating full report for ${EASY_ACCOUNT:-all depositors}..."
/opt/dans.knaw.nl/easy-manage-deposit/bin/easy-manage-deposit report full $EASY_ACCOUNT > $REPORT_FULL
exit_if_failed "full report failed"

echo -n "Creating full report from the last 24 hours for ${EASY_ACCOUNT:-all depositors}..."
/opt/dans.knaw.nl/easy-manage-deposit/bin/easy-manage-deposit report full --age 0 $EASY_ACCOUNT > $REPORT_FULL_24
exit_if_failed "full report failed"

echo "Status of $EASY_HOST deposits d.d. $(date) for depositor: ${EASY_ACCOUNT:-all}" | \
mail -s "$EASY_HOST Report: status of EASY deposits (${EASY_ACCOUNT:-all depositors})" \
     -a $REPORT_SUMMARY \
     -a $REPORT_SUMMARY_24 \
     -a $REPORT_FULL \
     -a $REPORT_FULL_24 \
     $BCC_EMAILS $FROM_EMAIL $TO
exit_if_failed "sending of e-mail failed"
