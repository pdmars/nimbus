import os
import sys
from pycb.cbException import cbException
import pycb
import stat
import urllib
import glob
import errno
import logging
import threading
import tempfile
import hashlib
import traceback
import time
import pycb

class cbPosixBackend(object):

    def __init__(self, installdir):
        self.base_dir = installdir

        try:
            os.mkdir(self.base_dir)
        except:
            pass
        try:
            os.mkdir(self.base_dir+"/buckets")
        except:
            pass

    # The POST request operation adds an object to a bucket using HTML forms.
    #
    #  Not implemented for now
    def post_object():
        ex = cbException('NotImplemented')
        raise ex


    # The PUT request operation adds an object to a bucket.
    #  In this operation we return a data object for writing to the cb
    #  store.  The controlling code will handle moving buffers to it
    # 
    # returns <return code>,<error msg>,<data object | None>
    # 0 indicates success
    # 
    def put_object(self, bucketName, objectName):
        # first make the bucket directory if it does not exist
        dir_name = bucketName[:1]
        bdir = self.base_dir + "/" + dir_name
        
        try:
            os.mkdir(bdir)
        except OSError, ose:
            if ose.errno != 17:
                raise

        fname = bucketName + "/" + objectName
        fname = fname.replace("/", "__")
        (osf, x) = tempfile.mkstemp(dir=bdir, suffix=fname)
        os.close(osf)
        data_key = x.strip()
        obj = cbPosixData(data_key, "w+b")
        return obj


    # Return 2 data objects, a source and dest.
    #
    # returns <return code>,<error msg>,<data object|None>,<data object|None>
    #
    def copy_object(self, srcObjectName, dstObjectName, moveOrCopy, httpHeaders):
        ex = cbException('NotImplemented')
        raise ex


    # returns a data object for reading.  The controlling code will handle
    # the buffer management.
    #
    # returns <return code>,<error msg>,<data object | None>
    def get_object(self, data_key):
        obj = cbPosixData(data_key, "r")
        return obj


    # The DELETE request operation removes the specified object from cb
    #   
    # returns <return code>,<error message | None>
    def delete_object(self, data_key):
        obj = cbPosixData(data_key, openIt=False)
        obj.delete()

    def get_size(self, data_key):
        st = os.stat(data_key)
        return st.st_size

    def get_mod_time(self, data_key):
        st = os.stat(data_key)
        return time.gmtime(st.st_ctime)

    def get_md5(self, data_key):
        obj = cbPosixData(data_key, "r")
        hash = obj.get_md5()
        obj.close()
        return hash

class cbPosixData(object):

    def __init__(self, data_key, access="r", openIt=True):
#       file like stuff
        #self.closed = True
        #self.encoding = 
        #self.errors
        #self.mode
        #self.name
        #self.newlines
        #self.softspace

        self.fname = data_key
        self.metafname = data_key + ".meta"
        self.data_key = data_key
        self.blockSize = pycb.config.block_size
        self.hashValue = None
        self.delete_on_close = False
        self.md5er = hashlib.md5()
        self.access = access

        if not openIt:
            return

        try:
            mFile = open(self.metafname, 'r')
            self.hashValue = mFile.readline()
            mFile.close()
        except:
            pass

        try:
            self.file = open(self.fname, access)
        except OSError, (OsEx):
            if OsEx.errno == errno.ENOENT:
               raise cbException('NoSuchKey')

    def get_mod_time(self):
        st = os.stat(self.data_key)
        return time.gmtime(st.st_ctime)

    def get_md5(self):
        if self.hashValue == None:
            if self.access == 'r':
                return None
            else:
                v = str(self.md5er.hexdigest()).strip()
                return v
        return self.hashValue

    def get_data_key(self):
        return self.data_key

    def get_size(self):
        return os.path.getsize(self.fname)

    def delete(self):
        try:
            os.unlink(self.metafname)
        except Exception, ex:
            pycb.log(logging.WARNING, "error deleting %s %s %s" % (self.metafname, str(sys.exc_info()[0]), str(ex)))
        try:
            os.unlink(self.data_key)
        except:
            pycb.log(logging.WARNING, "error deleting %s %s" % (self.data_key, str(sys.exc_info()[0])))

    def set_md5(self, hash):
        self.hashValue = hash

    # this is part of the work around for twisted
    def set_delete_on_close(self, delete_on_close):
        self.delete_on_close = delete_on_close

    # implement file-like methods
    def close(self):
        hashValue = self.get_md5()
        if hashValue != None:
            try:
                mFile = open(self.metafname, 'w')
                mFile.write(hashValue)
                mFile.close()
            except:
                pass

        try:
            self.file.close()
        except Exception, ex:
            pycb.log(logging.WARNING, "error closing data object %s %s" % (self.fname, sys.exc_info()[0]), tb=traceback)
        if self.delete_on_close:
            pycb.log(logging.INFO, "deleting the file on close %s" % (self.fname))
            self.delete()


    def flush(self):
        return self.file.flush()

    #def fileno(self):
    #def isatty(self):

    def next(self):
        return self.file.next()

    def read(self, size=None):
        if size == None:
            return self.file.read(self.blockSize)
        else:
            return self.file.read(size)

#    def readline(self, size=None):
#    def readlines(self, size=None):
#    def xreadlines(self):

    def seek(self, offset, whence=None):
        return self.file.seek(offset, whence)


#    def tell(self):
#    def truncate(self, size=None):

    def write(self, st):
        self.file.write(st)
        self.md5er.update(st)

    def writelines(self, seq):
        for s in seq:
            self.write(s)
        


