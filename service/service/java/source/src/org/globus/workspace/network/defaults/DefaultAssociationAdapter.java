/*
 * Copyright 1999-2008 University of Chicago
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.globus.workspace.network.defaults;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.globus.workspace.network.Association;
import org.globus.workspace.network.AssociationAdapter;
import org.globus.workspace.network.AssociationEntry;
import org.globus.workspace.persistence.PersistenceAdapter;
import org.globus.workspace.Lager;
import org.nimbustools.api.services.rm.ResourceRequestDeniedException;
import org.nimbustools.api.services.rm.ManageException;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class DefaultAssociationAdapter implements AssociationAdapter {

    private static final Log logger =
        LogFactory.getLog(DefaultAssociationAdapter.class.getName());

    private static final String MAC_MESSAGE =
        "When choosing MAC addresses to use, ensure you choose a unicast address.\n" +
        "That is, one with the low bit of the first octet set to zero. For example, an\n" +
        "address starting aa: is OK but ab: is not. It is best to keep to the range of\n" +
        "addresses declared to be \"locally assigned\" (rather than allocated globally to\n" +
        "hardware vendors). These have the second lowest bit set to one in the first\n" +
        "octet. For example, aa: is OK, a8: isn't.\n" +
        "\n" +
        "In summary, an address of the following form should be OK:\n" +
        "\n" +
        "XY:XX:XX:XX:XX:XX\n" +
        "\n" +
        "where X is any hexadecimal digit, and Y is one of 2, 6, A or E";

    private final Object lock = new Object();

    private List allMacs;

    private static final String[] zeroLen = new String[0];

    private final PersistenceAdapter persistence;
    private final Lager lager;

    private Resource networksDir;

    private String macPrefix;
    private String netSampleNetwork;
    private Resource netSampleResource;
    private Resource dhcpdEntriesResource;
    private Resource ipMacResource;

    public DefaultAssociationAdapter(PersistenceAdapter db,
                                     Lager lagerImpl) {
        if (db == null) {
            throw new IllegalArgumentException("db may not be null");
        }
        this.persistence = db;

        if (lagerImpl == null) {
            throw new IllegalArgumentException("lagerImpl may not be null");
        }
        this.lager = lagerImpl;
    }

    
    // -------------------------------------------------------------------------
    // implements AssociationAdapter
    // -------------------------------------------------------------------------

    public String[] getAssociationNames() throws ManageException {
        
        final Hashtable associations = this.persistence.currentAssociations(false);
        if (associations == null || associations.isEmpty()) {
            return zeroLen;
        } else {
            final Set keys = associations.keySet();
            return (String[])keys.toArray(new String[keys.size()]);
        }
    }

    public Object[] getNextEntry(String name, int vmid)

            throws ResourceRequestDeniedException {

        if (this.persistence == null) {
            throw new ResourceRequestDeniedException(
                    "networking initialization problem");
        }

        synchronized(this.lock) {
            return Util.getNextEntry(name, this.persistence,
                                     vmid, this.lager.eventLog);
        }
    }

    public void retireEntry(String name, String ipAddress, int trackingID)

            throws ManageException {
        
        if (this.persistence == null) {
            throw new ManageException(
                    "networking initialization problem");
        }

        synchronized(this.lock) {
            Util.retireEntry(name, ipAddress, this.persistence, trackingID);
        }
        
    }

    // -------------------------------------------------------------------------
    // IoC INIT METHOD
    // -------------------------------------------------------------------------

    public void validate() throws Exception {

        if (this.macPrefix != null) {
            this.validateMacPrefix();
            logger.info("MAC prefix: \"" + this.macPrefix + "\"");
        }

        if (this.networksDir != null) {

            final File associationDir = this.networksDir.getFile();

            Hashtable previous_associations;
            try {
                previous_associations = this.persistence.currentAssociations(false);
            } catch (ManageException e) {
                logger.error("",e);
                previous_associations = null;
            }

            final Hashtable new_associations =
                                Util.loadDirectory(associationDir,
                                                   previous_associations);
                
            if (this.macPrefix != null) {
                long mstart = 0;
                if (logger.isDebugEnabled()) {
                    mstart = System.currentTimeMillis();
                }
                this.allMacs = MacUtil.handleMacs(previous_associations,
                        new_associations,
                        this.macPrefix);
                if (logger.isDebugEnabled()) {
                    final long mstop = System.currentTimeMillis();
                    logger.debug("MAC handling took " +
                            Long.toString(mstop - mstart) + "ms.");
                }
            }

            this.persistence.replaceAssocations(new_associations);

            final Enumeration en = new_associations.keys();
            while (en.hasMoreElements()) {
                final String assocName = (String) en.nextElement();
                final Association assoc =
                        (Association) new_associations.get(assocName);
                int numEntries = 0;
                if (assoc.getEntries() != null) {
                    numEntries = assoc.getEntries().size();
                }

                if (numEntries == 1) {
                    logger.info("Network '" +
                            assocName + "' loaded with one address.");
                } else {
                    logger.info("Network '" +
                            assocName + "' loaded with " + numEntries +
                            " addresses.");
                }
            }

            // we write network info to various files (dhcpd entries, etc)
            this.writeNetworkFiles(new_associations);
        }
    }

    private void writeNetworkFiles(Map<String,Association> associations) {
        if (this.netSampleResource != null) {
            if (this.netSampleNetwork != null && this.netSampleNetwork.length() != 0) {
                final Association assoc = associations.get(this.netSampleNetwork);
                
                if (assoc == null || assoc.getEntries() == null || assoc.getEntries().isEmpty()) {
                    logger.warn ("Not writing netsample file because network '" +
                            this.netSampleNetwork + "' does not exist or has no entries");
                } else {
                    final List entries = assoc.getEntries();
                    final File netsampleFile;
                    try {

                        netsampleFile = this.netSampleResource.getFile();

                        FileUtil.writeNetSample(netsampleFile,
                                (AssociationEntry) entries.get(0),
                            this.netSampleNetwork, assoc.getDns());

                    } catch (IOException e) {
                        logger.warn("Failed to write netsample file to "+
                            this.netSampleResource.getFilename(), e);
                    }
                }
            }
        }

        if (this.dhcpdEntriesResource != null) {
            try {
                final File dhcpdEntriesFile = this.dhcpdEntriesResource.getFile();
                FileUtil.writeDhcpdEntries(dhcpdEntriesFile, associations);
            } catch (IOException e) {
                logger.warn("Failed to write dhcpd entries file to: "+
                        this.dhcpdEntriesResource.getFilename(), e);
            }
        }

        if (this.ipMacResource != null) {
            try {
                final File ipMacFile = this.ipMacResource.getFile();
                FileUtil.writeIpMacPairs(ipMacFile, associations);
            } catch (IOException e) {
                logger.warn("Failed to write IP -> MAC pairs to file: " +
                        this.ipMacResource.getFilename(), e);
            }
        }
    }

    private void validateMacPrefix() {
        if (this.macPrefix.length() > 17) {
            throw new IllegalArgumentException("MAC prefix is too long, " +
                    " it is " + this.macPrefix.length() +
                    " characters, max is 17");
        }

        if (!MacUtil.isValidMac(this.macPrefix, true)) {
            throw new IllegalArgumentException(
                    "MAC prefix has invalid characters or format: "+
                            this.macPrefix);
        }

        final char[] macChars = this.macPrefix.toCharArray();

        if (macChars.length > 1) {
            boolean ok = false;
            switch (macChars[1]) {
                case '2': ok = true; break;
                case '6': ok = true; break;
                case 'A': ok = true; break;
                case 'E': ok = true; break;
            }

            if (!ok) {
                final String choice = "\n\nYou're seeing this message" +
                        " because you chose '" + macChars[1] +
                        "' as the second character in MAC prefix \"" +
                        this.macPrefix + "\"\n";
                throw new IllegalArgumentException(MAC_MESSAGE + choice);
            }
        }
    }

    public String newMAC() throws ResourceRequestDeniedException {
        if (this.macPrefix == null || this.allMacs == null) {
            return null;
        } else {
            return MacUtil.pickNew(this.allMacs, this.macPrefix);
        }
    }

    public void setNetworksDir(Resource dir) {
        this.networksDir = dir;
    }

    public void setMacPrefix(String prefix) {
        if (prefix == null || prefix.trim().length() == 0) {
            this.macPrefix = null;
        } else {
            this.macPrefix = prefix.toUpperCase();
        }
    }

    public void setNetSampleResource(Resource netSampleResource) {
        this.netSampleResource = netSampleResource;
    }

    public Resource getNetSampleResource() {
        return netSampleResource;
    }

    public void setDhcpdEntriesResource(Resource dhcpdEntriesResource) {
        this.dhcpdEntriesResource = dhcpdEntriesResource;
    }

    public Resource getDhcpdEntriesResource() {
        return dhcpdEntriesResource;
    }

    public void setIpMacResource(Resource ipMacResource) {
        this.ipMacResource = ipMacResource;
    }

    public Resource getIpMacResource() {
        return ipMacResource;
    }

    public String getNetSampleNetwork() {
        return netSampleNetwork;
    }

    public void setNetSampleNetwork(String netSampleNetwork) {
        this.netSampleNetwork = netSampleNetwork;
    }
}
