package org.globus.workspace;

import org.globus.workspace.manager.DelegatingManager;
import org.nimbustools.api.repr.Caller;
import org.nimbustools.api.repr.vm.State;
import org.nimbustools.api.services.rm.StateChangeCallback;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.concurrent.CountDownLatch;

public class BackfillTermination implements StateChangeCallback {

    private static final Log logger =
            LogFactory.getLog(BackfillTermination.class.getName());

    protected DelegatingManager manager;
    String vmidStr;
    CountDownLatch terminations;
    Caller caller;

    public BackfillTermination(String vmidStr,
                               CountDownLatch terminations,
                               Caller caller,
                               DelegatingManager manager) {
        this.vmidStr = vmidStr;
        this.terminations = terminations;
        this.caller = caller;
        this.manager = manager;
    }
    
    public void newState(State state) {
        if ((state.getState().compareTo("Propagated")) == 0) {
            try {
                this.manager.trash(this.vmidStr, 0, this.caller);
            } catch (Exception e) {
                logger.error("Problem trashing backfill VM: " +
                        e.getMessage());
            }
            this.terminations.countDown();
        }
        else {
            logger.debug("Expecting backfill state change to propagated, " +
                    "instead state changed to: " + state.getState());
            this.terminations.countDown();
        }
        
    }
}