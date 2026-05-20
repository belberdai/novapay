package dev.novapay.analytics.aggregation

import dev.novapay.analytics.exception.AccountNotFoundException
import kotlinx.coroutines.runBlocking
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/analytics/accounts")
class AccountAggregateController(
    private val repository: AccountAggregateRepository,
) {
    @GetMapping("/{accountId}")
    fun getAggregate(@PathVariable accountId: UUID): AccountAggregateResponse = runBlocking {
        val aggregate = repository.findByAccountId(accountId)
            ?: throw AccountNotFoundException(accountId)
        AccountAggregateResponse.from(aggregate)
    }
}