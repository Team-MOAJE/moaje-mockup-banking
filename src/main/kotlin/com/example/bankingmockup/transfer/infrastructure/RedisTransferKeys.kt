package com.example.bankingmockup.transfer.infrastructure

object RedisTransferKeys {
    fun transfer(clientTransferId: String): String = "{banking}:transfer:$clientTransferId"
}
