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

package org.nimbustools.api.defaults.repr.vm;

import org.nimbustools.api._repr.vm._VM;
import org.nimbustools.api.repr.vm.NIC;
import org.nimbustools.api.repr.vm.ResourceAllocation;
import org.nimbustools.api.repr.vm.Schedule;
import org.nimbustools.api.repr.vm.State;
import org.nimbustools.api.repr.vm.VMFile;
import org.nimbustools.api.repr.Caller;

import java.util.Arrays;

public class DefaultVM implements _VM {

    // -------------------------------------------------------------------------
    // INSTANCE VARIABLES
    // -------------------------------------------------------------------------

    private String id;
    private String groupid;
    private String coschedid;
    private NIC[] nics;
    private VMFile[] vmFiles;
    private ResourceAllocation resourceAllocation;
    private Schedule schedule;
    private State state;
    private Caller creator;
    private int launchIndex;
    private String mdUserData;
    private String credentialName;
    private String sshKeyName;
    private String lifeCycle;
    private String spotReqID;
    private String clientToken;
    private String details = null;


    // -------------------------------------------------------------------------
    // implements org.nimbustools.api.repr.vm.VM
    // -------------------------------------------------------------------------

    public String getID() {
        return this.id;
    }

    public String getGroupID() {
        return this.groupid;
    }

    public String getCoschedID() {
        return this.coschedid;
    }

    public NIC[] getNics() {
        return this.nics;
    }

    public VMFile[] getVMFiles() {
        return this.vmFiles;
    }

    public ResourceAllocation getResourceAllocation() {
        return this.resourceAllocation;
    }

    public Schedule getSchedule() {
        return this.schedule;
    }

    public State getState() {
        return this.state;
    }

    public Caller getCreator() {
        return this.creator;
    }

    public int getLaunchIndex() {
        return this.launchIndex;
    }

    public String getSshKeyName() {
        return sshKeyName;
    }
    
    public String getLifeCycle() {
        return this.lifeCycle;
    }    
    
    public String getSpotInstanceRequestID(){
        return this.spotReqID;
    }

    public String getClientToken() {
        return clientToken;
    }

    // -------------------------------------------------------------------------
    // implements org.nimbustools.api._repr.vm._VM
    // -------------------------------------------------------------------------

    public void setID(String id) {
        this.id = id;
    }

    public void setGroupID(String id) {
        this.groupid = id;
    }

    public void setCoschedID(String id) {
        this.coschedid = id;
    }

    public void setNics(NIC[] nics) {
        this.nics = nics;
    }

    public void setVMFiles(VMFile[] vmFiles) {
        this.vmFiles = vmFiles;
    }

    public void setResourceAllocation(ResourceAllocation resourceAllocation) {
        this.resourceAllocation = resourceAllocation;
    }

    public void setSchedule(Schedule schedule) {
        this.schedule = schedule;
    }

    public void setState(State state) {
        this.state = state;
    }

    public void setCreator(Caller creator) {
        this.creator = creator;
    }

    public void setLaunchIndex(int launchIndex) {
        this.launchIndex = launchIndex;
    }

    public void setMdUserData(String mdUserData) {
        this.mdUserData = mdUserData;
    }

    public String getMdUserData() {
        return this.mdUserData;
    }

    public void setCredentialName(String credentialName) {
        this.credentialName = credentialName;
    }

    public String getCredentialName() {
        return this.credentialName;
    }

    public void setSshKeyName(String sshKeyName) {
        this.sshKeyName = sshKeyName;
    }
    
    public void setLifeCycle(String lifeCycle) {
        this.lifeCycle = lifeCycle;
    }    
    
    public void setSpotInstanceRequestID(String spotReqID){
        this.spotReqID = spotReqID;
    }

    public void setClientToken(String clientToken) {
        this.clientToken = clientToken;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getDetails() {
        return this.details;
    }


    // -------------------------------------------------------------------------
    // DEBUG STRING
    // -------------------------------------------------------------------------

    public String toString() {        
        return "DefaultVM [coschedid=" + coschedid
                + ", groupid=" + groupid + ", id=" + id + ", launchIndex="
                + launchIndex + ", lifeCycle=" + lifeCycle + ", userDataPresent="
                + (this.mdUserData != null) + ", nics=" + Arrays.toString(nics)
                + ", resourceAllocation=" + resourceAllocation + ", schedule="
                + schedule + ", spotReqID=" + spotReqID + 
                ", state=" + state + "]";
    }   
}
