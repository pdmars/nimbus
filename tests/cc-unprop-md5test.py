#!/usr/bin/env python

import shutil
import pexpect
import sys
import os
import filecmp
import uuid
import datetime

tst_image_name = os.environ['NIMBUS_TEST_IMAGE']
tst_image_src = os.environ['NIMBUS_SOURCE_TEST_IMAGE']
bkname=os.path.join(os.environ['HOME'], ".s3cfg.common")
s3cfg=os.path.join(os.environ['HOME'], ".s3cfg")
s3cfguser=os.path.join(os.environ['HOME'], ".s3cfg.reg")
os.rename(s3cfg, bkname)
shutil.copyfile(s3cfguser, s3cfg)
try:
    to=int(os.environ["NIMBUS_TEST_TIMEOUT"])
    cc_home=os.environ['CLOUD_CLIENT_HOME']
    logfile = sys.stdout
    newname=str(uuid.uuid1()).replace("-", "")
    localfile=str(uuid.uuid1()).replace("-", "")

    src_file = tst_image_src
    sfa = src_file.split("/")
    image_name = sfa[len(sfa) - 1]
    size=os.path.getsize(src_file)

    cmd = "%s/bin/cloud-client.sh --transfer --sourcefile %s" % (cc_home, src_file)
    (x, rc)=pexpect.run(cmd, withexitstatus=1, logfile=logfile)

    cmd = "%s/bin/cloud-client.sh --run --name %s --hours .25" % (cc_home, image_name)
    child = pexpect.spawn (cmd, timeout=to, maxread=20000, logfile=logfile)
    rc = child.expect ('Running:')
    if rc != 0:
        print "Running: not found in the list"
        sys.exit(1)
    handle = child.readline().strip().replace("'", "")
    rc = child.expect(pexpect.EOF)
    if rc != 0:
        print "run"
        sys.exit(1)

    cmd = "%s/bin/cloud-client.sh --handle %s --save --newname %s" % (cc_home, handle, newname)
    print cmd
    (x, rc)=pexpect.run(cmd, withexitstatus=1, logfile=logfile)
    print x
    if rc != 0:
    	print "failed to save"
        sys.exit(1)

    # down load the new name with s3cmd
    cmd="s3cmd get s3://Repo/VMS/%s/%s %s" % (os.environ['NIMBUS_TEST_USER_CAN_ID'], image_name, localfile)
    print cmd
    (x, rc)=pexpect.run(cmd, withexitstatus=1, logfile=logfile)
    print x
    if rc != 0:
        print "failed to save"
        sys.exit(1)
    rc = filecmp.cmp(localfile, tst_image_src)
    os.remove(localfile)
    if not rc:
        print "files differ"
        if 'NIMBUS_TEST_MODE_REAL' not in os.environ:
            sys.exit(1)

    cmd="s3cmd info s3://Repo/VMS/%s/%s" % (os.environ['NIMBUS_TEST_USER_CAN_ID'], newname)
    print cmd
    child = pexpect.spawn (cmd, timeout=to, maxread=20000, logfile=logfile)
    rc = child.expect ('MD5 sum:')
    if rc != 0:
        print "MD% sum not found in the list"
        sys.exit(1)
    sum1 = child.readline().strip()
    rc = child.expect(pexpect.EOF)
    if rc != 0:
        print "s3 info failed"
        sys.exit(1)
    cmd="s3cmd info s3://Repo/VMS/%s/%s" % (os.environ['NIMBUS_TEST_USER_CAN_ID'], image_name)
    print cmd
    child = pexpect.spawn (cmd, timeout=to, maxread=20000, logfile=logfile)
    rc = child.expect ('MD5 sum:')
    if rc != 0:
        print "MD5 not found in the list"
        sys.exit(1)
    sum2 = child.readline().strip()
    rc = child.expect(pexpect.EOF)
    if rc != 0:
        print "s3 info failed"
        sys.exit(1)

    if sum1 != sum2:
        print "sums not the same |%s| |%s|" % (sum1, sum2)
        print sum1
        print sum2
        if 'NIMBUS_TEST_MODE_REAL' not in os.environ:
            sys.exit(1)

    cmd = "%s/bin/cloud-client.sh --delete --name %s" % (cc_home, newname)
    (x, rc)=pexpect.run(cmd, withexitstatus=1, logfile=logfile)
    cmd = "%s/bin/cloud-client.sh --delete --name %s" % (cc_home, image_name)
    (x, rc)=pexpect.run(cmd, withexitstatus=1, logfile=logfile)


    sys.exit(0)
finally:
    shutil.copyfile(bkname, s3cfg)


