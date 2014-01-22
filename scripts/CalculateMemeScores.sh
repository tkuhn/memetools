#!/bin/bash
#
# The following environment variable can be used:
# - JAVA_OPTS: Can be used to set Java command line options

CLASS=ch.tkuhn.memetools.CalculateMemeScores

DIR=`pwd`
cd "$( dirname "${BASH_SOURCE[0]}" )"
cd ..
MEMETOOLSJAVADIR=`pwd`

# for Cygwin:
MEMETOOLSJAVADIR=${MEMETOOLSJAVADIR#/cygdrive/?}

cd $DIR

mvn -q -e -f $MEMETOOLSJAVADIR/pom.xml exec:java -Dexec.mainClass="$CLASS" -Dexec.args="$*"
