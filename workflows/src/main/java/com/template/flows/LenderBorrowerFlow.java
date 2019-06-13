package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.template.contracts.LendingProposalContract;
import com.template.states.LenderBorrowerState;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

@StartableByRPC
@InitiatingFlow
public class LenderBorrowerFlow extends FlowLogic<SignedTransaction> {

    private StateAndRef inputStateAndRef;
    private LenderBorrowerState outputState;

    public LenderBorrowerFlow(StateAndRef inputStateAndRef, LenderBorrowerState outputState) {
        this.inputStateAndRef = inputStateAndRef;
        this.outputState = outputState;
    }


    private final ProgressTracker.Step LENDING_BORROWER_REQUEST = new ProgressTracker.Step("Lending proposal application for company");
    private final ProgressTracker.Step VERIFICATION_TRANSACTION = new ProgressTracker.Step("Verifying contract constraints.");
    private final ProgressTracker.Step PLATFORM_RESPONSE = new ProgressTracker.Step("Sending response from Bank");
    private final ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing transaction with our private key.");
    private final ProgressTracker.Step GATHERING_SIGNS = new ProgressTracker.Step("Gathering the counterParty's signature.") {
        public ProgressTracker childProgressTracker() {
            return CollectSignaturesFlow.Companion.tracker();
        }
    };

    private final ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
        public ProgressTracker childProgressTracker() {
            return FinalityFlow.Companion.tracker();
        }
    };

    private final ProgressTracker progressTracker = new ProgressTracker(
            LENDING_BORROWER_REQUEST,
            VERIFICATION_TRANSACTION,
            SIGNING_TRANSACTION,
            PLATFORM_RESPONSE,
            GATHERING_SIGNS,
            FINALISING_TRANSACTION
    );

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }


    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {
        getLogger().info("Entered into flow");
        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        progressTracker.setCurrentStep(LENDING_BORROWER_REQUEST);
        final Command<LendingProposalContract.Commands.LendingBorrower> lendingBorrowerCommand = new Command<>(new LendingProposalContract.Commands.LendingBorrower(), ImmutableList.of(outputState.getMyParty().getOwningKey(), outputState.getOtherParty().getOwningKey()));
        System.out.println("Transaction creating");
        final TransactionBuilder lenderBorrowerTransaction = new TransactionBuilder(notary)
                .addInputState(inputStateAndRef)
                .addOutputState(outputState)
                .addCommand(lendingBorrowerCommand);
        System.out.println("Transaction created");
        progressTracker.setCurrentStep(VERIFICATION_TRANSACTION);
        lenderBorrowerTransaction.verify(getServiceHub());
        System.out.println("Transaction verified");
        progressTracker.setCurrentStep(SIGNING_TRANSACTION);
        final SignedTransaction halfSignedTx = getServiceHub().signInitialTransaction(lenderBorrowerTransaction);
        System.out.println("Partly SignedTransaction is  " + halfSignedTx);
        progressTracker.setCurrentStep(GATHERING_SIGNS);
        FlowSession otherPartySession = initiateFlow(outputState.getOtherParty());
        final SignedTransaction finalSignedTx = subFlow(new CollectSignaturesFlow(halfSignedTx, ImmutableSet.of(otherPartySession), CollectSignaturesFlow.Companion.tracker()));
        progressTracker.setCurrentStep(FINALISING_TRANSACTION);
        System.out.println("Fully SignedTransaction is  " + finalSignedTx);
        return subFlow(new FinalityFlow(finalSignedTx, ImmutableSet.of(otherPartySession)));

    }
}
