package com.example.bankingmockup.account.infrastructure

object RedisAccountKeys {
    fun account(accountNumber: String): String = "{banking}:account:$accountNumber"
    fun accountOwnerIndex(ownerCi: String, bankCode: String, productName: String): String =
        "{banking}:account-owner:$ownerCi:$bankCode:$productName"
    fun providerAccountIndex(providerAccountId: String): String = "{banking}:provider-account:$providerAccountId"

    fun history(accountNumber: String): String = "{banking}:account:history:$accountNumber"
}
