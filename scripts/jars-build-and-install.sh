#!/bin/bash

NIMBUS_PRINTNAME="build and install all (jars only)"
NIMBUS_ANT_CMD="deploy-jars-GT4.0-service -Dbuild.also=x $*"

BASEDIR_REL="`dirname $0`/.."
BASEDIR=`cd $BASEDIR_REL; pwd`
RUN=$BASEDIR/scripts/lib/gt4.0/build/run.sh

echo ""
echo "*** Just JAR installation -- development target.  Requires previous installation!"

export NIMBUS_PRINTNAME NIMBUS_ANT_CMD
exec sh $RUN
