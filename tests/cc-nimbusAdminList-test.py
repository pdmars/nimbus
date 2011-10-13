#!/usr/bin/env python

import pexpect
import sys
import os
import re

to=90
cc_home=os.environ['CLOUD_CLIENT_HOME']
nimbus_home=os.environ['NIMBUS_HOME']
nimbus_user=os.environ['NIMBUS_TEST_USER']
logfile = sys.stdout

try:
	os.mkdir("%s/history/vm-999" % (cc_home))
except:
	print "The directory already exists"
	pass

cmd = "%s/bin/nimbus-list-users %%" % (nimbus_home)
(x, rc)=pexpect.run(cmd, withexitstatus=1)
print x

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

cmd = "%s/bin/nimbus-admin --list --user %s" % (nimbus_home, nimbus_user)
print cmd
(x, rc)=pexpect.run(cmd, withexitstatus=1)
print x
if rc != 0 or not re.match(".*id\s*?:\s*?\d.*", x):
    print "error"
    sys.exit(1)

cmd = "%s/bin/nimbus-admin --list --dn /O=Auto/OU=CA/CN=%s" % (nimbus_home, nimbus_user)
print cmd
(x, rc)=pexpect.run(cmd, withexitstatus=1)
print x
if rc != 0 or not re.match(".*id\s*?:\s*?\d.*", x):
	print "error"
	sys.exit(1)

cmd = "%s/bin/nimbus-admin --list --host localhost" % (nimbus_home)
print cmd
(x, rc)=pexpect.run(cmd, withexitstatus=1)
print x
if rc != 0 or not re.match(".*id\s*?:\s*?\d.*", x):
    print "error"
    sys.exit(1)

cmd = "%s/bin/nimbus-admin --list --gid 1" % (nimbus_home)
print cmd
(x, rc)=pexpect.run(cmd, withexitstatus=1)
print x
if rc != 0 or not re.match(".*id\s*?:\s*?\d.*", x):
    print "error"
    sys.exit(1)

cmd = "%s/bin/nimbus-admin --list --gname UNLIMITED" % (nimbus_home)
print cmd
(x, rc)=pexpect.run(cmd, withexitstatus=1)
print x
if rc != 0 or not re.match(".*id\s*?:\s*?\d.*", x):
    print "error"
    sys.exit(1)

cmd = "%s/bin/nimbus-admin --list" % (nimbus_home)
print cmd
(x, rc)=pexpect.run(cmd, withexitstatus=1)
print x
if rc != 0 or not re.match(".*id\s*?:\s*?\d.*", x):
    print "error"
    sys.exit(1)

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

cmd = "%s/bin/nimbus-admin --nodes" % (nimbus_home)
print cmd
(x, rc)=pexpect.run(cmd, withexitstatus=1)
print x
if rc != 0:
    print "error"
    sys.exit(1)
if not re.search("node\s*:\s*localhost", x):
    print "not showing localhost node?"
    sys.exit(1)
if not re.search("id\s*:\s*\d*,\s\d*", x):
    print "not showing two vms on localhost node?"
    sys.exit(1)

cmd = "%s/bin/nimbus-admin --batch --shutdown --all" % (nimbus_home)
print cmd
(x, rc)=pexpect.run(cmd, withexitstatus=1)
print x
if rc != 0:
    print "error"
    sys.exit(1)

cmd = "%s/bin/cloud-client.sh --delete --name group" % (cc_home)
(x, rc)=pexpect.run(cmd, withexitstatus=1)
if rc != 0:
    print "error"
    sys.exit(1)

sys.exit(0)
