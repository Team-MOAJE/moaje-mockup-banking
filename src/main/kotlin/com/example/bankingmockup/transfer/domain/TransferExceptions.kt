package com.example.bankingmockup.transfer.domain

sealed class TransferBusinessException(message: String) : RuntimeException(message)

class SameAccountTransferException :
    TransferBusinessException("fromAccountNumber and toAccountNumber must be different")
