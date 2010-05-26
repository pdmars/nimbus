#!/usr/bin/python -u

"""*
 * Copyright 2009 University of Victoria
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * AUTHOR - Adam Bishop - ahbishop@uvic.ca
 *       
 * For comments or questions please contact the above e-mail address 
 * or Ian Gable - igable@uvic.ca
 *
 * """

__VERSION__ = '1.0'

import sys
import commands
import os
import logging
from cStringIO import StringIO
import libvirt
from optparse import OptionParser
import socket
import time
import string

# NAGIOS Plug-In API return code values

NAGIOS_RET_OK = 0
NAGIOS_RET_WARNING = 1
NAGIOS_RET_CRITICAL = 2
NAGIOS_RET_UNKNOWN = 3


class Logger:
    """ A class to encapsulate useful logging features and setup

    """
    def __init__(self, name, errorLogFile=None):

        self.logger = logging.getLogger(name)

        self.logger.setLevel(logging.INFO)
        formatter = logging.Formatter('%(asctime)s : %(name)s : %(levelname)s : %(message)s')

        nagiosObservableHndlr = logging.StreamHandler(sys.stdout)
        nagiosObservableHndlr.setLevel(logging.INFO)
        nagiosObservableHndlr.setFormatter(formatter)
        if(errorLogFile != None):
            pass

        # Nagios plug-ins fail to report properly if this log file is opened...

   #     fileOutputHndlr = logging.FileHandler(errorLogFile)
    #    fileOutputHndlr.setFormatter(formatter)
    #    fileOutputHndlr.setLevel(logging.DEBUG)

     #   self.logger.addHandler(fileOutputHndlr)
        self.logger.addHandler(nagiosObservableHndlr)

    def warning(self, msg):
        self.logger.warning(msg)

    def info(self, msg):
        self.logger.info(msg)

    def error(self, msg):
        self.logger.error(msg)

    def debug(self, msg):
        self.logger.debug(msg)

# A recursive helper method that will iterate through an arbitraryly complex python data strcuture
# and generate the appropriate XML document mirroring the data structure layout
def _createXMLWorker(data, currentOutput):

    if (type(data) == type(dict())):
        # Don't write out a 'DNP' tag
        if "DNP" in data.keys():
           currentOutput.write(str(data["DNP"]))
           return
        for key in data.keys():
            currentOutput.write("<"+str(key)+">")
            _createXMLWorker(data[key], currentOutput)
            currentOutput.write("</"+str(key)+">")

    elif (type(data) == type(list())):
        for key in range(len(data)):
            _createXMLWorker(data[key], currentOutput)

    else:
        currentOutput.write(str(data))
        return

def pluginExitN(messageIdentifier, pluginInfo, returnCode):

    # This method should be the only exit point for all the plug-ins. This ensures that 
    # Nagios requirements are meant and performance XML data is properly formatted to work
    # with the rest of the Monitoring system. 
    # Do NOT just call sys.exit in the code 

    outputString = StringIO()
    outputString.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")

    localIP = (socket.gethostbyaddr( socket.gethostname() ))[2][0]
    outputString.write("<Node>")

    outputString.write("<"+messageIdentifier+" id=\""+messageIdentifier+localIP+"\""+" node=\""+localIP +"\">")
    _createXMLWorker(pluginInfo, outputString)
    outputString.write("</"+messageIdentifier+">")
    outputString.write("</Node>")

    sys.stdout.write(messageIdentifier+" | "+ outputString.getvalue()+"\n")
    sys.exit(returnCode)

class PluginObject:    
    """
        A simple base class for setting up logging and initialzing the pluginOutput structure

    """
    def __init__(self, callingClass):
        self.logger = Logger(callingClass)
        self.pluginOutput = {}

class Virtualized(PluginObject):
    """ This class is designed to be a "Abstract Base Class" in C++ terms, meaning it is intended to
        be derived from to complete functionality. All the libvirt lookups take place here to centralize
        logic and make creating derived plug-ins simple and uniform
    """
    # Lookup the available "domains" from libvirt, and other usefull stuff
    def __init__(self):
        PluginObject.__init__(self,self.__class__.__name__)
        #Establish a connection to the local VM Hypervisor (XEN)
        self.VMConnection  = libvirt.openReadOnly(None)
        if self.VMConnection == None:
            self.logger.error('Unable to open a Read-Only connection to local Hypervisor')
            sys.exit(NAGIOS_RET_CRITICAL)
        
        self.VMDomainsID = self.VMConnection.listDomainsID()
        self.VMs={}
        for id in self.VMDomainsID:
            #So, the VMs 'dictionary' stores virDomain (libvirt) objects
            try:
            # Skip over Dom0 or Domain-0 (hypervisor)
                if(id == 0):
                    continue
                self.VMs[id] = (self.VMConnection.lookupByID(id))
            except:
                self.logger.error("Failed to lookup a VM from a VMConnection by \'id\'")
                sys.exit(NAGIOS_RET_CRITICAL)

# I thought I'd have to define the __call__ function, but it seems like the 
# ctr is doing what I need it to for the 'Callback' functionality
class VMMemory(Virtualized):
    """ This class will look up the amount of memory/RAM currently allocated to each VM on the 
        local node. This is done through libvirt calls in the 'Virtualized' base class
        
        Used memory is reported back in MB.
    """
    #This ctr's interface is as such to match the 'callback' interface of the optionParser
    def __init__(self): #,option, opt_str, value, parser):
        Virtualized.__init__(self)
        self.resourceName = 'VM-Memory'

    def __call__(self, option, opt_str, value, parser):
        self.pluginOutput = []
        for vm in self.VMs.values():
            # vm.maxMemory reports 'kB' of memory, but we want to report 'MB' to be consistent across all plugins
            # hence, vm.maxMemory/1024 will convert from 'kB'->'MB'
#                              vm.name()
            tdict = {}
            tdict["VM"] = {"AllocatedMB":str(vm.maxMemory()/1024)}
            self.pluginOutput.append(tdict)

        pluginExitN(self.resourceName, self.pluginOutput, NAGIOS_RET_OK)

class HostVirt(Virtualized):
    """ This class will determine what virtualization technology is being used on each Virtual Machine
        running on the local node. This is done through a libvirt call in the 'Virtualized' base class
    """
    def __init__(self):
        Virtualized.__init__(self)
        self.resourceName = 'VirtualizationTech'

    def __call__(self,option, opt_str, value,parser):
        self.pluginOutput["Type"] = str(self.VMConnection.getType())
        pluginExitN(self.resourceName, self.pluginOutput, NAGIOS_RET_OK)

        
# Below is from libvirt.org and documents the layout of the data structure returned from the getInfo() call
# While this is a C struct, the layout and format is identical in Python

# Currently this info is useful for the VMCpuCores, VMCpuFreq and VMCpuArch plugins

        #struct virNodeInfo{

        #charmodel[32]  model   : string indicating the CPU model
        #unsigned long  memory  : memory size in kilobytes
        #unsigned int   cpus    : the number of active CPUs
        #unsigned int   mhz     : expected CPU frequency
        #unsigned int   nodes   : the number of NUMA cell, 1 for uniform mem access
        #unsigned int   sockets : number of CPU socket per node
        #unsigned int   cores   : number of core per socket
        #unsigned int   threads : number of threads per core
        #}


class HostCpuCores(Virtualized):
    """ This class will determine the number of CPU cores running on the local node. This is done through a libvirt
        call done in the 'Virtualized' base class
    """
    def __init__(self):
        Virtualized.__init__(self)
        self.resourceName = 'CPUCores'

    def __call__(self, option, opt_str, value, parser):

        tempRes = self.VMConnection.getInfo()
        # DNP means Do Not Print and thus the 'DNP' tag will not be included in the plug-ins XML output, only the 
        # value itself
        self.pluginOutput["DNP"] = str(tempRes[2])
        pluginExitN(self.resourceName, self.pluginOutput, NAGIOS_RET_OK)        

class HostCpuId(Virtualized):
    """ This class determines the CPU frequency of the local nodes processor. This was tested with a uniprocessor
    machine only, so a multi-CPU-socket machine with different core speeds may report differently. I believe that
    only the latest, most cutting edge CPUs from Intel (Nehalem) have this capability....
    """ 
    
    def __init__(self):
        Virtualized.__init__(self)
        self.resourceName = 'CPUID'

    def __call__(self,option, opt_str, value, parser):

        tempRes = self.VMConnection.getInfo()
        descr= ""
        try:
            cpuFile = open("/proc/cpuinfo","r")
            for line in cpuFile.readlines():
                # 'model name' is included by the linux environment in /proc/cpuinfo
                if(line.find("model name") != -1):
                    # the final split() then join() call is to remove extraneous whitespace
                    descr= string.join((line.split(':')[1]).split())
            cpuFile.close()
        except IOError, err:
            self.logger.error(str(err))
        self.pluginOutput["Description"] = str(descr)
        pluginExitN(self.resourceName, self.pluginOutput, NAGIOS_RET_OK)

class HostCpuArch(Virtualized):
    """ The class determines what the underlying CPU architecture is, and uses this information to determine
        whether the machine is running in 32bit or 64bit mode. 
 
        Only valid and tested on x86 and x86_64 class hardware, and modern hardware at that. The ARCH_MAP structure should
        provide the necessary architecture types.
    """

    ARCH_MAP = {    "i686"  : "x86",
                        "i386"  : "x86",
                        "x86_64":"x86_64"}
           # This entry seems rebarbative but it provides a uniform interface

    def __init__(self):
        Virtualized.__init__(self)
        self.resourceName = 'CPUArch'

    def __call__(self, option, opt_str, value,parser):
        tempRes = self.VMConnection.getInfo()
        self.pluginOutput["MicroArchitecture"] = self.ARCH_MAP[tempRes[0]]
        pluginExitN(self.resourceName, self.pluginOutput, NAGIOS_RET_OK)

class HostMemory(Virtualized):
    """ This class determines how much total memory and free memory remains on the local node with 
        all current virtual machines booted. This lookup is again done through a libvirt call in 
        the 'Virtualized' base class. 

        Memory values are reported in MB.
    """
    def __init__(self):
        Virtualized.__init__(self)
        self.resourceName = "Memory"

    def __call__(self,option,opt_str,value,parser):
        usedMemory = 0
        for vm in self.VMs.values():
            usedMemory +=  vm.info()[2]
         
        totalMemory = int(self.VMConnection.getInfo()[1])
        
        availableMemory =totalMemory -(usedMemory/1024)
        self.pluginOutput["TotalMB"] = str(totalMemory)
        self.pluginOutput["FreeMB"] = str(availableMemory)
        pluginExitN(self.resourceName, self.pluginOutput, NAGIOS_RET_OK)


class PluginCmdLineOpts(PluginObject):
    """ This class acts as the "central dispatcher" for determining what resource will be reported back
    to Nagios. Command line parameters act as the switches and determine which of the above classes
    gets instantianted.
    """
    def __init__(self):
        PluginObject.__init__(self,self.__class__.__name__)
        # Parse command-line options.
        parser = OptionParser()

        #The following options are parsed to conform with Nagios Plug-In "standard practices"
        parser.add_option("-V","--version",dest="version", \
            action="store_false", help="Diplay version information",default=True)

        parser.add_option("--VMmem", help="Discover the of memory dedicated to each VM (in MB)", action="callback", callback=VMMemory())
        parser.add_option("--HostCpuArch",help="Discover the host CPU architecture (x86 or x86_64)", action="callback", callback=HostCpuArch())        
        parser.add_option("--HostVirt", help="Discover the host virtualization technology",action="callback", callback=HostVirt())
        parser.add_option("--HostCpuID",help="Discover the host CPU Info", action="callback", callback=HostCpuId())
        parser.add_option("--HostCpuCores",help="Discover the number of host CPU cores", action="callback", callback=HostCpuCores())
        parser.add_option("--HostMemory",help="Discover the total and free memory (in MB) of host",action="callback",callback=HostMemory())

        self.parser = parser

    # This method is also responsible for "picking" what resource to monitor via the appropriate
    # command line switches (which I need to define). I don't want a single, monolithic script
    # running for ALL the resources, since this waters down NAGIOS's monitoring capabilities
    # (since that would make only a single resource to monitor)
    # Instead, this one script will be executed multiple time with different commandline options
    # to facilitate the monitoring of the different resources independant of one another
    def validate(self):

        # Parse the command line arguments and store them in 'options'
        (options, args) = self.parser.parse_args()


# The "main" code starts here & begins execution here
if __name__ == '__main__':

    testObject = PluginCmdLineOpts()
    testObject.validate()

    # This line should never be reached in any code path
    sys.exit(NAGIOS_RET_CRITICAL)
