package dev.novapay.analytics.exception

import java.util.UUID

class AccountNotFoundException(accountId: UUID) :
    RuntimeException("No analytics data found for account: $accountId")