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

package org.globus.workspace.service.binding.authorization;

import org.nimbustools.api.services.rm.ResourceRequestDeniedException;
import org.nimbustools.api.services.rm.AuthorizationException;
import org.globus.workspace.service.binding.vm.VirtualMachine;

import javax.security.auth.Subject;

public interface CreationAuthorizationCallout {

    public boolean isEnabled();

    // ResourceRequestDeniedException is for Deny + message for client
    public Integer isPermitted(VirtualMachine[] bindings,
                               String callerDN,
                               Subject subject,
                               Long elapsedMins,
                               Long reservedMins,
                               int numWorkspaces,
                               double chargeRatio)

            throws AuthorizationException,
                   ResourceRequestDeniedException;
}
