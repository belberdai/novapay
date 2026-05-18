package dev.novapay.analytics

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<AnalyticsServiceApplication>().with(TestcontainersConfiguration::class).run(*args)
}
