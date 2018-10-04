package com.lankydanblog.tutorial.services

import com.lankydanblog.tutorial.flows.ReplyToMessageFlow
import com.lankydanblog.tutorial.states.MessageState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.Vault
import net.corda.core.node.services.trackBy
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@CordaService
class MessageObserver(private val serviceHub: AppServiceHub) : SingletonSerializeAsToken() {

  private companion object {
    val log = loggerFor<MessageObserver>()
    val executor: Executor = Executors.newFixedThreadPool(8)!!
  }

  init {
    replyToNewMessages()
    log.info("Tracking new messages")
  }

  private fun replyToNewMessages() {
    val ourIdentity = ourIdentity()
    serviceHub.vaultService.trackBy<MessageState>().updates.subscribe { update: Vault.Update<MessageState> ->
      update.produced.forEach { message: StateAndRef<MessageState> ->
        val state = message.state.data
        if (state.recipient == ourIdentity) {
          executor.execute {
            log.info("Replying to message ${message.state.data.contents}")
            serviceHub.startFlow(ReplyToMessageFlow(message))
          }
        }
      }
    }
  }

  private fun ourIdentity(): Party = serviceHub.myInfo.legalIdentities.first()
}