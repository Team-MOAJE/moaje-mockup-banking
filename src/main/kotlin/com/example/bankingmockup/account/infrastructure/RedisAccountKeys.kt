package com.example.bankingmockup.account.infrastructure

object RedisAccountKeys {
    fun account(accountNumber: String): String = "{banking}:account:$accountNumber"
    fun history(accountNumber: String): String = "{banking}:account:history:$accountNumber"
}
