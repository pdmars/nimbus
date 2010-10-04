import sys
import os
from socket import *
import logging
import pylantorrent
from pylantorrent.ltException import LTException
from pylantorrent.ltConnection import LTConnection
import json
import traceback
import threading
import hashlib

#  The first thing sent is a json header terminated by a single line
#  of EOH
#
#  {
#      host
#      port
#      length
#      requests = [ {filename, id, rename}, ]
#      destinations =
#       [ {
#           host
#           port
#           requests = [ { filename, id, rename } ]
#           block_size
#       }, ]
#  }
#
class LTServer(object):

    def __init__(self, inf, outf):
        self.lock = threading.Lock()
        self.json_header = {}
        self.inf = inf
        self.outf = outf
        self.max_header_lines = 102400
        self.block_size = 128*1024
        self.read_header()
        self.suffix = ".lattorrent"
        self.created_files = []

    def clean_up(self):
        for f in self.created_files:
            try:
                os.remove(f)
            except:
                pass

    def read_header(self):
        max_header_lines = 256
        pylantorrent.log(logging.INFO, "reading a new header")

        count = 0
        lines = ""
        l = self.inf.readline()
        while l:
            ndx = l.find("EOH : ")
            if ndx == 0:
                break
            lines = lines + l
            l = self.inf.readline()
            count = count + 1
            if count == self.max_header_lines:
                raise LTException(501, "%d lines long, only %d allowed" % (count, max_header_lines))
        if l == None:
            raise LTException(501, "No signature found")
        signature = l[len("EOH : "):].strip()

        auth_hash = pylantorrent.get_auth_hash(lines)

        if auth_hash != signature:
            pylantorrent.log(logging.INFO, "ACCESS DENIED |%s| != |%s| -->%s<---" % (auth_hash, signature, lines))
            raise LTException(508, "%s is a bad signature" % (auth_hash))

        self.json_header = json.loads(lines)

        # verify the header
        try:
            reqs = self.json_header['requests']
            for r in reqs:
                filename = r['filename']
                rid = r['id']
                rn = r['rename']

            host = self.json_header['host']
            port = int(self.json_header['port'])
            urls = self.json_header['destinations']
            self.degree = int(self.json_header['degree'])
            self.data_length = long(self.json_header['length'])
        except Exception, ex:
            raise LTException(502, str(ex), traceback)

    def print_results(self, s):
        pylantorrent.log(logging.DEBUG, "printing\n--------- %s\n---------------" % (s))
#        self.lock.acquire()
        try:
            self.outf.write(s)
            self.outf.write(os.linesep)
        finally:
#            self.lock.release()
            pass
 
    def get_valid_vcons(self, destinations):
        v_con_array = []

        while len(destinations) > 0 and len(v_con_array) < self.degree:
            ep = destinations.pop(0)
            try:
                v_con = LTConnection(ep, self)
                v_con_array.append(v_con)
            except LTException, vex:
                # i think this is the only recoverable error
                # keep track of them and return in output
                s = vex.get_printable()
                self.print_results(s)

        each = len(destinations) / self.degree
        rem = len(destinations) % self.degree
        ndx = 0
        for v_con in v_con_array:
            end = ndx + each + rem
            mine = destinations[ndx:end]
            rem = 0
            v_con.send_header(mine)

        return v_con_array

    def store_and_forward(self):

        header = self.json_header
        ex_array = []
        requests_a = header['requests']

        files_a = []
        for req in requests_a:
            filename = req['filename']
            rid = req['id']
            try:
                rn = req['rename']
                if rn:
                    filename = filename + self.suffix
                f = open(filename, "w")
                files_a.append(f)
                self.created_files.append(filename)
            except Exception, ex:
                pylantorrent.log(logging.ERROR, "Failed to open %s" % (filename))
                raise LTException(503, str(ex), header['host'], int(header['port']), reqs=requests_a)

        destinations = header['destinations']
        v_con_array = self.get_valid_vcons(destinations)

        try:
            md5er = hashlib.md5()
            read_count = 0
            bs = self.block_size
            data = "X"  # fke data value to prime the loop
            while data and read_count < self.data_length:
                if bs + read_count > self.data_length:
                    bs = self.data_length - read_count
                data = self.inf.read(bs)
                if data:
                    md5er.update(data)
                    for v_con in v_con_array:
                        v_con.send(data)
                    for f in files_a:
                        f.write(data)
                    read_count = read_count + len(data)
            md5str = str(md5er.hexdigest()).strip()
        except Exception, ex:
            for v_con in v_con_array:
                v_con.close()
            for f in files_a:
                f.close()
            raise ex
        for f in files_a:
            f.close()

        for req in requests_a:
            realname = req['filename']
            rn = req['rename']
            if rn:
                tmpname = realname + self.suffix
                os.rename(tmpname, realname)
                self.created_files.remove(tmpname)

        # close all the connections
        for v_con in v_con_array:
            v_con.read_output()
            v_con.close()

        pylantorrent.log(logging.DEBUG, "All data sent %s %d" % (md5str, len(requests_a)))
        # if we got to here it was successfully written to a file
        # and we can call it success.  Print out a success message for 
        # everyfile written
        vex = LTException(0, "Success", header['host'], int(header['port']), requests_a, md5sum=md5str)
        s = vex.get_printable()
        self.print_results(s)


def main(argv=sys.argv[1:]):

    pylantorrent.log(logging.INFO, "server starting")
    v = None
    rc = 1
    try:
        v = LTServer(sys.stdin, sys.stdout)
        v.store_and_forward()
        rc = 0
    except LTException, ve:
        pylantorrent.log(logging.ERROR, "error %s" % (str(ve)), traceback)
        s = ve.get_printable()
        print s
    except Exception, ex:
        pylantorrent.log(logging.ERROR, "error %s" % (str(ex)), traceback)
        vex = LTException(500, str(ex))
        s = vex.get_printable()
        print s
    finally:
        if v != None:
            v.clean_up()
        print "EOD"

    return rc

if __name__ == "__main__":
    rc = main()
    sys.exit(rc)

