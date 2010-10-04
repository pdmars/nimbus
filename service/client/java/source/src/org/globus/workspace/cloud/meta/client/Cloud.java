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

package org.globus.workspace.cloud.meta.client;

import org.globus.workspace.client_core.ParameterProblem;
import org.globus.workspace.client_core.ExecutionProblem;
import org.globus.workspace.cloud.client.Props;
import org.globus.workspace.cloud.client.AllArgs;
import org.globus.workspace.cloud.client.util.MetadataXMLUtil;
import org.globus.workspace.cloud.client.util.CloudClientUtil;
import org.globus.workspace.cloud.client.util.DeploymentXMLUtil;
import org.globus.workspace.common.SecurityUtil;
import org.globus.gsi.GlobusCredential;
import org.nimbustools.messaging.gt4_0.generated.metadata.VirtualWorkspace_Type;
import org.nimbustools.messaging.gt4_0.generated.negotiable.WorkspaceDeployment_Type;
import org.apache.axis.types.URI;

import java.util.Properties;
import java.io.InputStream;
import java.io.IOException;
import org.globus.workspace.common.print.Print;

/**
 * Cloud-specific properties
 */
public class Cloud {



    private Cloud() {}

    public static Cloud createFromProps(String name, Properties props) throws ParameterProblem {

        if (name == null) {
            throw new  IllegalArgumentException("name cannot be null");
        }

        if (props == null) {
            throw new IllegalArgumentException("props cannot be null");
        }

        Cloud cloud = new Cloud();
        cloud.name = name;
        cloud.intakeProperties(props);

        return cloud;
    }

    public static Properties loadDefaultProps() throws IOException {
        final Properties props = new Properties();
        InputStream is = null;
        try {
            is = Cloud.class.getResourceAsStream("default.properties");
            if (is == null) {
                throw new IOException("Problem loading default properties");
            }
            props.load(is);
        } finally {
            if (is != null) {
                is.close();
            }
        }
        return props;
    }

    private String name;

    //  CACHE FIELDS FOR DERIVED VALUES
    private String remoteUserBaseDir;
    private GlobusCredential credentialUsed;


    /**
     * This is hopefully a shortlived hack.
     * We do some special-case nastiness for EC2.
     */
    private boolean isEC2;


    //  CONFIGURATION PROPS

    private String factoryHostPort;
    private String factoryID;
    private String gridftpHostPort;
    private String gridftpID;
    private String metadata_mountAs;
    private String metadata_cpuType;
    private String metadata_vmmVersion;
    private String metadata_vmmType;
    private String targetBaseDirectory;
    private boolean propagationKeepPort;
    private int memory;
    private int cores = -1;
    private int pollTime;
    private String brokerPublicNicPrefix;
    private String brokerLocalNicPrefix;
    private Properties props;


    private void intakeProperties(Properties props) throws ParameterProblem {

        this.props = props;

        this.factoryHostPort = getRequiredProp(props,
            Props.KEY_FACTORY_HOSTPORT);
        this.factoryID = getRequiredProp(props,
            Props.KEY_FACTORY_IDENTITY);
        this.gridftpHostPort = getRequiredProp(props,
            Props.KEY_XFER_HOSTPORT);
        this.gridftpID = getRequiredProp(props,
            Props.KEY_GRIDFTP_IDENTITY);
        this.metadata_mountAs = getRequiredProp(props,
            Props.KEY_METADATA_MOUNTAS);
        this.metadata_cpuType = getRequiredProp(props,
            Props.KEY_METADATA_CPUTYPE);
        this.metadata_vmmVersion = getRequiredProp(props,
            Props.KEY_METADATA_VMMVERSION);
        this.metadata_vmmType = getRequiredProp(props,
            Props.KEY_METADATA_VMMTYPE);
        this.targetBaseDirectory = getRequiredProp(props,
            Props.KEY_TARGET_BASEDIR);
        this.brokerLocalNicPrefix = getRequiredProp(props,
            Props.KEY_BROKER_LOCAL);
        this.brokerPublicNicPrefix = getRequiredProp(props,
            Props.KEY_BROKER_PUB);

        final String keepPortStr = getRequiredProp(props,
            Props.KEY_PROPAGATION_KEEPPORT);
        this.propagationKeepPort = Boolean.valueOf(keepPortStr);

        final String memoryStr =
            getRequiredProp(props, Props.KEY_MEMORY_REQ);
        this.memory = Integer.parseInt(memoryStr);

        final String coresStr = props.getProperty(Props.KEY_CORES_REQ);
        if (coresStr != null) {
            this.cores = Integer.parseInt(coresStr);
            if (this.cores < 1) {
                throw new ParameterProblem("Invalid core # requested in the " +
                        "properties of cloud '"+this.name+"'");
            }
        }

        final String pollStr =
            getRequiredProp(props, Props.KEY_POLL_INTERVAL);
        this.pollTime = Integer.parseInt(pollStr);

    }

    private String getRequiredProp(Properties props, String name)
        throws ParameterProblem {
        String val = props.getProperty(name);
        if (val == null) {
            throw new ParameterProblem("Required parameter '"+name+
                "' is missing from the properties of cloud '"+this.name+"'");
        }
        return val;
    }

    GlobusCredential getCredentialBeingUsed() throws Exception {
        if (this.credentialUsed != null) {
            return this.credentialUsed;
        }
        this.credentialUsed = CloudClientUtil.getActiveX509Credential(new Print());
        if (this.credentialUsed == null) {
            throw new Exception("Could not find current credential");
        }
        return this.credentialUsed;
    }

    String getRemoteUserBaseDir() throws ExecutionProblem {

        if (this.remoteUserBaseDir != null) {
            return this.remoteUserBaseDir;
        }

        Exception ex = null;
        String hash = null;
        try {
            hash = SecurityUtil.hashGlobusCredential(this.getCredentialBeingUsed(), null);
        } catch (Exception e) {
            ex = e;
        }
        if (hash == null) {
            throw new ExecutionProblem("Could not obtain hash of current " +
                "credential to generate directory name", ex);
        }

        this.remoteUserBaseDir =
                CloudClientUtil.destUserBaseDir(this.targetBaseDirectory,
                                                hash);

        return this.remoteUserBaseDir;
    }

    private URI deriveImageURL(String imageName) throws ExecutionProblem {

        AllArgs args = new AllArgs(new Print());
        try {
            args.intakeProperties(this.props, "from meta", null);
        } catch (Exception e) {
            throw new ExecutionProblem(e.getMessage(), e);
        }
        String urlString = CloudClientUtil.deriveImageURL(imageName, args);

        try {
            return new URI(urlString);
        } catch (URI.MalformedURIException e) {
            throw new ExecutionProblem(e.getMessage(), e);
        }
    }

    public VirtualWorkspace_Type generateMetadata(MemberDeployment member)
        throws ExecutionProblem {

        if (member == null) {
            throw new IllegalArgumentException("member may not be null");
        }

        return MetadataXMLUtil.constructMetadata(
            this.deriveImageURL(member.getImageName()),
            this.getMetadata_mountAs(),
            null,
            member.getMember().getAssociations(),
            member.getMember().getIfaceNames(),
            this.getMetadata_cpuType(),
            this.getMetadata_vmmVersion(),
            this.getMetadata_vmmType(),
            null);
    }

    public WorkspaceDeployment_Type generateDeployment(MemberDeployment member,
                                                       int durationMinutes)
        throws ExecutionProblem {

        if (member == null) {
            throw new IllegalArgumentException("member may not be null");
        }
        if (durationMinutes < 1) {
            throw new IllegalArgumentException("durationMinutes must be > 0");
        }

        return DeploymentXMLUtil.constructDeployment(
            durationMinutes,
            this.memory,
            this.cores,
            null, // we aren't supporting new prop targets yet in this client
            member.getInstanceCount()
        );
    }


    // accessors

    public String getMetadata_mountAs() {
        return metadata_mountAs;
    }

    public String getMetadata_cpuType() {
        return metadata_cpuType;
    }

    public String getMetadata_vmmVersion() {
        return metadata_vmmVersion;
    }

    public String getMetadata_vmmType() {
        return metadata_vmmType;
    }

    public String getName() {
        return name;
    }

    public boolean isEC2() {
        return isEC2;
    }

    public String getFactoryHostPort() {
        return factoryHostPort;
    }

    public String getFactoryID() {
        return factoryID;
    }

    public String getGridftpHostPort() {
        return gridftpHostPort;
    }

    public String getGridftpID() {
        return gridftpID;
    }

    public String getWorkspaceFactoryURL() {
        return CloudClientUtil.serviceURL(this.factoryHostPort);
    }

    public int getPollTime() {
        return pollTime;
    }


    public String getBrokerPublicNicPrefix() {
        return brokerPublicNicPrefix;
    }

    public String getBrokerLocalNicPrefix() {
        return brokerLocalNicPrefix;
    }
}
