package com.lankydanblog.tutorial.flows

import co.paralleluniverse.fibers.Suspendable
import com.lankydanblog.tutorial.contracts.MessageContract
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import com.lankydanblog.tutorial.states.MessageState

@InitiatingFlow
@StartableByRPC
class SendNewMessageFlow(private val message: MessageState) : FlowLogic<SignedTransaction>() {
  @Suspendable
  override fun call(): SignedTransaction {
    return subFlow(SendMessageFlow(message))
  }
}