package com.lankydanblog.tutorial.flows

import co.paralleluniverse.fibers.Suspendable
import com.lankydanblog.tutorial.states.MessageState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction

@InitiatingFlow
@StartableByRPC
class ReplyToMessageFlow(private val message: StateAndRef<MessageState>) : FlowLogic<SignedTransaction>() {

  @Suspendable
  override fun call(): SignedTransaction {
    return subFlow(SendMessageFlow(response(message), message))
  }

  private fun response(message: StateAndRef<MessageState>): MessageState {
    val state = message.state.data
    return state.copy(
        contents = "Thanks for your message: ${state.contents}",
        recipient = state.sender,
        sender = state.recipient
    )
  }
}