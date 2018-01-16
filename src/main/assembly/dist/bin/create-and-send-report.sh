#!/usr/bin/env bash
#
# Helper script to create full and summary reports and send them to a list of recipients.
#
# Usage: ./create-and-send-report.sh <depositor-account> <from-email> <to-email> <bcc-email>
#
# Use - (dash) as depositor-account to generate a report for all the deposits.
#

EASY_ACCOUNT=$1
FROM=$2
TO=$3
BCC=$4
TMPDIR=/tmp

if [ "$EASY_ACCOUNT" == "-" ]; then
    EASY_ACCOUNT=""
fi

DATE=$(date +%Y-%m-%d)
REPORT_SUMMARY=/tmp/report-summary-${EASY_ACCOUNT:-all}-$DATE.txt
REPORT_FULL=/tmp/report-full-${EASY_ACCOUNT:-all}-$DATE.csv


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
    local MSG=$1
    EXITSTATUS=$?
    if [ $EXITSTATUS != 0 ]; then
        echo "ERROR: $MSG, exit status = $EXITSTATUS"
        exit 1
    fi
    echo "OK"
}

echo -n "Creating summary report for ${EASY_ACCOUNT:-all depositors}..."
/opt/dans.knaw.nl/easy-manage-deposit/bin/easy-manage-deposit summary $EASY_ACCOUNT > $REPORT_SUMMARY
exit_if_failed "summary report failed"
echo -n "Creating full report for ${EASY_ACCOUNT:-all depositors}..."
/opt/dans.knaw.nl/easy-manage-deposit/bin/easy-manage-deposit full $EASY_ACCOUNT > $REPORT_FULL
exit_if_failed "full report failed"

echo "Status of EASY deposits d.d. $(date) for depositor: ${EASY_ACCOUNT:-all}" | \
mail -s "Report: status of EASY deposits (${EASY_ACCOUNT:-all depositors})" \
     -a $REPORT_SUMMARY \
     -a $REPORT_FULL \
     $FROM_EMAIL $BCC_EMAILS $TO
exit_if_failed "sending of e-mail failed"