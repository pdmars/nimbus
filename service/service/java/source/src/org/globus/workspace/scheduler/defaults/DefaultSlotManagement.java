/*
 * Copyright 1999-2010 University of Chicago
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

package org.globus.workspace.scheduler.defaults;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.globus.workspace.Lager;
import org.globus.workspace.ProgrammingError;
import org.globus.workspace.persistence.WorkspaceDatabaseException;
import org.globus.workspace.scheduler.*;
import org.globus.workspace.persistence.PersistenceAdapter;
import org.globus.workspace.service.InstanceResource;
import org.globus.workspace.service.WorkspaceHome;
import org.globus.workspace.service.binding.vm.VirtualMachine;
import org.globus.workspace.service.binding.vm.VirtualMachineDeployment;
import org.nimbustools.api.services.rm.ResourceRequestDeniedException;
import org.nimbustools.api.services.rm.DoesNotExistException;
import org.nimbustools.api.services.rm.ManageException;

import java.util.*;

/**
 * Needs dependency cleanups
 */
public class DefaultSlotManagement implements SlotManagement, NodeManagement {

    // -------------------------------------------------------------------------
    // STATIC VARIABLES
    // -------------------------------------------------------------------------
    
    private static final Log logger =
        LogFactory.getLog(DefaultSlotManagement.class.getName());


    // -------------------------------------------------------------------------
    // INSTANCE VARIABLES
    // -------------------------------------------------------------------------

    private final PersistenceAdapter db;
    private final Lager lager;
    private WorkspaceHome home;

    private boolean greedy;

    private Hashtable backfillVMs = new Hashtable();
    private Set oldBackfillIDs = new HashSet();


    // -------------------------------------------------------------------------
    // CONSTRUCTOR
    // -------------------------------------------------------------------------

    public DefaultSlotManagement(PersistenceAdapter db,
                                 Lager lager) {

        if (db == null) {
            throw new IllegalArgumentException("db may not be null");
        }
        this.db = db;

        if (lager == null) {
            throw new IllegalArgumentException("lager may not be null");
        }
        this.lager = lager;
    }

    
    // -------------------------------------------------------------------------
    // MODULE SET (avoids circular dependency problem)
    // -------------------------------------------------------------------------

    public void setHome(WorkspaceHome homeImpl) {
        if (homeImpl == null) {
            throw new IllegalArgumentException("homeImpl may not be null");
        }
        this.home = homeImpl;
    }

    
    // -------------------------------------------------------------------------
    // SET
    // -------------------------------------------------------------------------


    public void setSelectionStrategy(String selectionStrategy) {

        // leave room for more options in the future
        final String RROBIN = "round-robin";
        final String GREEDY = "greedy";
        
        if (RROBIN.equalsIgnoreCase(selectionStrategy)) {
            this.greedy = false;
        } else if (GREEDY.equalsIgnoreCase(selectionStrategy)) {
            this.greedy = true;
        } else {
            throw new IllegalArgumentException(
                    "Unknown VMM selection strategy: '" + selectionStrategy + "'.  This " +
                            "scheduler only accepts: '" + RROBIN + "' and '" + GREEDY + '\'');
        }
    }
    
    // -------------------------------------------------------------------------
    // implements SlotManagement
    // -------------------------------------------------------------------------
    
    /**
     * @param req a single workspace or homogenous group-workspace request
     * @return Reservation res
     * @throws ResourceRequestDeniedException exc
     */
    public synchronized Reservation reserveSpace(NodeRequest req)

            throws ResourceRequestDeniedException {

        if (req == null) {
            throw new IllegalArgumentException("req is null");
        }

        final int[] vmids = req.getIds();

        final String[] hostnames =
                this.reserveSpace(vmids, req.getMemory(),
                                  req.getNeededAssociations());

        if (req.getBackfillReq() == true) {
            for (int i=0; i < vmids.length; i++) {
                this.backfillVMs.put(vmids[i], hostnames[i]);
            }
            logger.debug("Added backfill node to the hashtable: " +
                         this.backfillVMs.toString());
        }

        return new Reservation(vmids, hostnames);
    }

    /**
     * @param requests an array of single workspace or homogenous
     *                 group-workspace requests
     * @param coschedid coscheduling (ensemble) ID
     * @return Reservation res
     * @throws ResourceRequestDeniedException exc
     */
    public synchronized Reservation reserveCoscheduledSpace(
                                                NodeRequest[] requests,
                                                String coschedid)
            throws ResourceRequestDeniedException {

        if (requests == null || requests.length == 0) {
            throw new IllegalArgumentException("requests null or length 0?");
        }

        final ArrayList idInts = new ArrayList(64);
        final ArrayList allHostnames = new ArrayList(64);
        final ArrayList allDurations = new ArrayList(64);
        final ArrayList allMemory = new ArrayList(64); // for backouts if needed

        try {
            for (int i = 0; i < requests.length; i++) {

                final NodeRequest request = requests[i];

                final int[] ids = request.getIds();
                if (ids == null) {
                    throw new ResourceRequestDeniedException(
                            "Cannot proceed, no ids in NodeRequest (?)");
                }

                final String[] hostnames =
                        this.reserveSpace(ids,
                                          request.getMemory(),
                                          request.getNeededAssociations());

                final Integer duration = new Integer(request.getDuration());

                for (int j = 0; j < ids.length; j++) {
                    idInts.add(new Integer(ids[j]));
                    allHostnames.add(hostnames[j]);
                    allDurations.add(duration);
                    allMemory.add(new Integer(request.getMemory()));
                }
            }
        } catch (Exception e) {
            String msg = "Problem reserving space for coscheduling group '" +
                         coschedid + "': " + e.getMessage();

            if (logger.isDebugEnabled()) {
                logger.error(msg, e);
            } else {
                logger.error(msg);
            }

            if (allHostnames.size() != allMemory.size()) {
                logger.fatal("Could not back reservations out, no matching " +
                        "memory recordings (?)");
                throw new ResourceRequestDeniedException(msg);
                }

            final String[] justReservedNodes = (String[])
                    allHostnames.toArray(new String[allHostnames.size()]);
            final Integer[] justReservedMemory = (Integer[])
                    allMemory.toArray(new Integer[allMemory.size()]);

            for (int i = 0; i < justReservedNodes.length; i++) {
                try {

                    ResourcepoolUtil.retireMem(justReservedNodes[i],
                                               justReservedMemory[i],
                                               this.db,
                                               this.lager.eventLog,
                                               this.lager.traceLog,
                                               -1);
            } catch (Exception ee) {
                    logger.error(ee.getMessage());
                }
            }

            throw new ResourceRequestDeniedException(msg);
        }

        final int length = idInts.size();

        final int[] all_ids = new int[length];
        final String[] all_hostnames = new String[length];
        final int[] all_durations = new int[length];

        for (int i = 0; i < length; i++) {
            all_ids[i] = ((Number)idInts.get(i)).intValue();
            all_hostnames[i] = (String)allHostnames.get(i);
            all_durations[i] = ((Integer)allDurations.get(i)).intValue();
        }

        return new Reservation(all_ids, all_hostnames, all_durations);
    }

    /**
     * Only handling one slot per VM for now, will change in the future
     * (multiple layers).
     *
     * @param vmids array of IDs.  If array length is greater than one, it is
     *        up to the implementation (and its configuration etc) to decide
     *        if each must map to its own node or not.  In the case where more
     *        than one VM is mapped to the same node, the returned node
     *        assignment array will include duplicates.
     * @param memory megabytes needed
     * @param assocs array of needed associations, can be null
     * @return Names of resources.  Must match length of vmids input and caller
     *         assumes the ordering in the assignemnt array maps to the input
     *         vmids array.
     *
     * @throws ResourceRequestDeniedException can not fulfill request
     */
    private String[] reserveSpace(final int[] vmids,
                                  final int memory,
                                  final String[] assocs)
                  throws ResourceRequestDeniedException {


        if (vmids == null) {
            throw new IllegalArgumentException("no vmids");
        }

        String msg = "request for " + vmids.length + " space(s) with " +
                "mem = " + memory;

        if (lager.traceLog) {

            if (assocs == null) {
                msg += ", needed networks null";
            } else if (assocs.length == 0) {
                msg += ", needed networks = zero length";
            } else {
                msg += ", needed networks = ";
                for (int i = 0; i < assocs.length; i++) {
                    msg += "[" + i + "] " + assocs[i];
                    if (i != assocs.length-1) {
                        msg += ", ";
                    }
                }
            }
            logger.trace(msg);
        } else {
            logger.debug(msg);
        }

        // in future, getResourcepoolEntry() will take a group request
        // (and as much as we will move down to SQL for efficiency)

        final String[] nodes = new String[vmids.length];
        int bailed = -1;
        Throwable failure = null;

        for (int i = 0; i < vmids.length; i++) {

            try {
                nodes[i] = ResourcepoolUtil.getResourcePoolEntry(memory,
                                                                 assocs,
                                                                 this.db,
                                                                 this.lager,
                                                                 vmids[i],
                                                                 greedy);
                if (nodes[i] == null) {
                    throw new ProgrammingError(
                                    "returned node should not be null");
                }
            } catch (Throwable t) {
                bailed = i;
                failure = t;
                break;
            }
        }

        if (failure == null) {
            return nodes;
        }

        // nothing to back out:
        if (bailed < 1) {
            if (failure instanceof ResourceRequestDeniedException) {
                throw (ResourceRequestDeniedException) failure;
            } else {
                throw new ResourceRequestDeniedException(
                                                failure.getMessage());
            }
        }

        final String clientMsg;
        if (bailed == 1) {
            clientMsg = "Problem reserving enough space (did get enough " +
                        "for one VM)";
        } else {
            clientMsg = "Problem reserving enough space (did get enough " +
                        "for " + bailed + " VMs)";
        }

        // back out
        for (int i = 0; i < bailed; i++) {
            try {
                ResourcepoolUtil.retireMem(nodes[i], memory, this.db,
                                           this.lager.eventLog,
                                           this.lager.traceLog,
                                           vmids[i]);
            } catch (Throwable t) {
                if (logger.isDebugEnabled()) {
                    logger.error(
                            "Error with one backout: " + t.getMessage(), t);
                } else {
                    logger.error(
                            "Error with one backout: " + t.getMessage());
                }
                // continue trying to backout anyhow
            }
        }

        throw new ResourceRequestDeniedException(clientMsg);
    }

    public boolean isBestEffort() {
        return false;
    }

    public boolean isEvacuationStrict() {
        return false;
    }

    public boolean isNeededAssociationsSupported() {
        return true;
    }
    
    public boolean canCoSchedule() {
        return true;
    }

    public synchronized void releaseSpace(final int vmid) throws ManageException {

        if (lager.traceLog) {
            logger.trace("releaseSpace(): " + Lager.id(vmid));
        }

        // get the necessary information from the resource, no reason (yet) to
        // double track it here even if that is in principle more encapsulated 

        // assuming resource vm correlation as usual
        final InstanceResource resource;
        try {
            // find will not return null
            resource = this.home.find(vmid);
        } catch (DoesNotExistException e) {
            logger.error("resource pool management received releaseSpace " +
                    "for workspace " + Lager.id(vmid)
                    + ", but WorkspaceHome failed to find it", e);
            return;
        } catch (Exception e) {
            if (logger.isDebugEnabled()) {
                logger.error(e.getMessage(), e);
            } else {
                logger.error(e.getMessage());
            }
            throw new ManageException(e);
        }

        logger.debug("found resource = " + resource);

        final VirtualMachine vm = resource.getVM();
        if (vm == null) {
            throw new ProgrammingError("vm is null");
        }

        final String node = vm.getNode();
        if (node == null) {
            logger.warn(Lager.id(vmid) + " assuming no node assignment yet " +
                    "because of ensemble not-done");
            return;
        }

        final VirtualMachineDeployment vmdep = vm.getDeployment();
        if (vmdep == null) {
            throw new ProgrammingError("deployment is null");
        }

        final int mem = vmdep.getIndividualPhysicalMemory();

        logger.debug("releaseSpace() retiring mem = " + mem +
                    ", node = '" + node + "' from " + Lager.id(vmid));

        ResourcepoolUtil.retireMem(node, mem, this.db,
                                   this.lager.eventLog, this.lager.traceLog,
                                   vmid);

        if (this.backfillVMs.remove(vmid) != null) {
            logger.debug("Removed backfill node from the hashtable: " +
                        this.backfillVMs.toString());
            this.oldBackfillIDs.add(vmid);
        } else {
            logger.debug("Failed to remove backfill node from the " +
                         "hashtable: " + Integer.toString(vmid));
        }
    }

    public void setScheduler(Scheduler adapter) {
        // ignored
    }


    // -------------------------------------------------------------------------
    // IoC INIT METHOD
    // -------------------------------------------------------------------------

    public synchronized void validate() throws Exception {

        if (home == null) {
            throw new Exception("home may not be null");
        }
    }


    public synchronized ResourcepoolEntry addNode(String hostname,
                                                  String pool,
                                                  String associations,
                                                  int memory,
                                                  boolean active)
            throws NodeExistsException {

        if (hostname == null) {
            throw new IllegalArgumentException("hostname may not be null");
        }
        hostname = hostname.trim();
        if (hostname.length() == 0) {
            throw new IllegalArgumentException("hostname may not be empty");
        }

        try {
            final ResourcepoolEntry existing =
                    this.db.getResourcepoolEntry(hostname);

            if (existing != null) {
                throw new NodeExistsException("A VMM node with the hostname "+
                        hostname+" already exists in the pool");
            }

            // This will catch the corner case of one or many VMs being started
            // on a node, the node being deleted from the configuration, the node
            // being RE-inserted into the configuration, all the while with no
            // VM memory being retired.

            final int memInUse =
                        this.db.memoryUsedOnPoolnode(hostname);

            final int correctCurrentMem = memory - memInUse;

            if (correctCurrentMem == memory) {
                if (lager.traceLog) {
                    logger.trace("curmem for VMM '" + hostname +
                            "' matches VM records");
                }
            } else {
                logger.info("Reconfiguration corner case, current " +
                        "memory-in-use record for VMM '" +
                        hostname +
                        "' was wrong, old value was " +
                        "0 MB, new value is " +
                        correctCurrentMem + " MB.");
            }

            final ResourcepoolEntry entry =
                    new ResourcepoolEntry(pool, hostname, memory,
                            correctCurrentMem, associations, active);

            //check then act protected by lock
            this.db.addResourcepoolEntry(entry);

            return entry;

        } catch (WorkspaceDatabaseException e) {
            // TODO ???
            throw new RuntimeException(e);
        }
    }

    public List<ResourcepoolEntry> getNodes() {
        try {
            return this.db.currentResourcepoolEntries();
        } catch (WorkspaceDatabaseException e) {
            // TODO ???
            throw new RuntimeException(e);
        }
    }

    public ResourcepoolEntry getNode(String hostname) {
        if (hostname == null) {
            throw new IllegalArgumentException("hostname may not be null");
        }
        hostname = hostname.trim();
        if (hostname.length() == 0) {
            throw new IllegalArgumentException("hostname may not be empty");
        }
        try {
            return this.db.getResourcepoolEntry(hostname);
        } catch (WorkspaceDatabaseException e) {
            // TODO ???
            throw new RuntimeException(e);
        }
    }

    /**
     * Updates an existing pool entry.
     *
     * Null values for any of the parameters mean no update to that field.
     * But at least one field must be specified.
     * @param hostname the node to be updated, required
     * @param pool the new resourcepool name, can be null
     * @param networks the new networks association list, can be null
     * @param memory the new max memory value for the node, can be null
     * @param active the new active state for the node, can be null
     * @return the updated ResourcepoolEntry
     * @throws NodeInUseException
     * @throws NodeNotFoundException
     */
    
    public synchronized ResourcepoolEntry updateNode(
            String hostname,
            String pool,
            String networks,
            Integer memory,
            Boolean active)
            throws NodeInUseException, NodeNotFoundException {

        try {

            Integer availMemory = null;
            if (memory != null) {
                final ResourcepoolEntry entry = getNode(hostname);
                if (entry == null) {
                    throw new NodeNotFoundException();
                }

                if (!entry.isVacant()) {
                    logger.info("Refusing to update VMM node "+ hostname+
                            " memory max while VMs are running");
                    throw new NodeInUseException();
                }
                availMemory = memory;
            }

            boolean updated = this.db.updateResourcepoolEntry(hostname,
                    pool, networks, memory, availMemory, active);
            if (!updated) {
                throw new NodeNotFoundException();
            }

            return getNode(hostname);
            
        } catch (WorkspaceDatabaseException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized boolean removeNode(String hostname)
            throws NodeInUseException {
        if (hostname == null) {
            throw new IllegalArgumentException("hostname may not be null");
        }
        hostname = hostname.trim();
        if (hostname.length() == 0) {
            throw new IllegalArgumentException("hostname may not be empty");
        }

        try {
            final ResourcepoolEntry entry =
                    this.db.getResourcepoolEntry(hostname);

            if (entry == null) {
                return false;
            }

            if (!entry.isVacant()) {
                throw new NodeInUseException("The VMM node "+ hostname +
                        " is in use and cannot be removed from the pool");
            }

            return this.db.removeResourcepoolEntry(hostname);

        } catch (WorkspaceDatabaseException e) {
            // TODO ???
            throw new RuntimeException(e);
        }

    }

    public int getBackfillVMID() {
        if (this.backfillVMs.isEmpty() == true) {
            return -1;
        } else {
            Enumeration e = this.backfillVMs.keys();
            String vmidStr = e.nextElement().toString();
            int vmid = Integer.parseInt(vmidStr.trim());
            return vmid;
        }
    }

    public boolean isOldBackfillID(int vmid) {
        return this.oldBackfillIDs.contains(vmid);
    }

    public boolean isCurrentBackfillID(int vmid) {
        return this.backfillVMs.contains(vmid);
    }
}
