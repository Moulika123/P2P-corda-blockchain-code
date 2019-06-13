package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.template.states.UserBankDetailsState;
import net.corda.core.contracts.ContractState;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;

import static net.corda.core.contracts.ContractsDSL.requireThat;

@InitiatedBy(BankDetailsUpdateFlow.class)
public class BankDetailsUpdateResponder extends FlowLogic<SignedTransaction> {
    private FlowSession counterPartySession;

    public BankDetailsUpdateResponder(FlowSession counterPartySession) {
        this.counterPartySession = counterPartySession;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {
        class SignTxFlow extends SignTransactionFlow {
            private SignTxFlow(FlowSession otherPartyFlow, ProgressTracker progressTracker) {
                super(otherPartyFlow, progressTracker);
            }

            @Override
            protected void checkTransaction(SignedTransaction stx) {

                requireThat(require -> {
                    ContractState output = stx.getTx().getOutputs().get(0).getData();
                    require.using("This must be a UserBankDetailsUpdating transaction.", output instanceof UserBankDetailsState);
                    return null;
                });
            }
        }
        final SignTxFlow signTxFlow = new SignTxFlow(counterPartySession, SignTransactionFlow.Companion.tracker());
        final SecureHash txId = subFlow(signTxFlow).getId();

        return subFlow(new ReceiveFinalityFlow(counterPartySession, txId));
    }
}
