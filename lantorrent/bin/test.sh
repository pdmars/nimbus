#!/bin/bash

# most users will not have xinetd in their path even if it is installed
export PATH=/usr/sbin/:$PATH
dir=`dirname $0`
cd $dir/..
pypath=`pwd`

cd ../
export NIMBUS_HOME=`pwd`
source $NIMBUS_HOME/ve/bin/activate

if [ "X${PYTHONPATH}" == "X" ]; then
    export PYTHONPATH=$pypath
else
    export PYTHONPATH=$pypath:${PYTHONPATH}
fi
# so that we pick up the ini file
export LANTORRENT_HOME=$pypath

pidfile=`mktemp`
$LANTORRENT_HOME/bin/make_lt_server.sh 4 $pidfile
xinet_pid=`cat $pidfile`
echo "xinet on $xinet_pid"

cd $LANTORRENT_HOME
$dir/lt-daemon &
ltd_pid=$!

trap "kill $xinet_pid $ltd_pid; sleep 10; kill -9 $xinet_pid $ltd_pid" EXIT
source $LANTORRENT_HOME/tests/ports_env.sh
nosetests tests/*_test.py
