package org.globus.workspace;

import org.globus.workspace.service.binding.vm.VirtualMachine;
import org.nimbustools.api.services.rm.AuthorizationException;

/**
 * Created by John Bresnahan
 * User: bresnaha
 * Date: May 22, 2010
 * Time: 9:06:39 AM
 * <p/>
 * org.globus.workspace
 */
public interface RepoFileSystemAdaptor
{
    public String translateExternaltoInternal(
        String                          publicName,
        VirtualMachine                  vm)
            throws WorkspaceException;

    public void unpropagationFinished(
        String                          publicName)
        throws WorkspaceException;

}
