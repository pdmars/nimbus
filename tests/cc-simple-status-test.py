#!/usr/bin/env python

import pexpect
import sys
import os

to=90
cc_home=os.environ['CLOUD_CLIENT_HOME']
logfile = sys.stdout

cmd = "%s/bin/cloud-client.sh --transfer --sourcefile /etc/group" % (cc_home)
(x, rc)=pexpect.run(cmd, withexitstatus=1)

cmd = "%s/bin/cloud-client.sh --run --name group --hours .25" % (cc_home)
child = pexpect.spawn (cmd, timeout=to, maxread=20000, logfile=logfile)
rc = child.expect ('Running:')
if rc != 0:
    print "group not found in the list"
    sys.exit(1)
handle = child.readline().strip().replace("'", "")
rc = child.expect(pexpect.EOF)
if rc != 0:
    print "run"
    sys.exit(1)

cmd = "%s/bin/cloud-client.sh --status" % (cc_home)
child = pexpect.spawn (cmd, timeout=to, maxread=20000, logfile=logfile)
rc = child.expect ('State:')
if rc != 0:
    print "group not found in the list"
    sys.exit(1)

cmd = "%s/bin/cloud-client.sh --terminate --handle %s" % (cc_home, handle)
print cmd
(x, rc)=pexpect.run(cmd, withexitstatus=1)
print x
if rc != 0:
    print "failed to terminate"
    sys.exit(1)

cmd = "%s/bin/cloud-client.sh --delete --name group" % (cc_home)
print cmd
(x, rc)=pexpect.run(cmd, withexitstatus=1)
print x
if rc != 0:
    print "failed to terminate"
    sys.exit(1)
sys.exit(0)

