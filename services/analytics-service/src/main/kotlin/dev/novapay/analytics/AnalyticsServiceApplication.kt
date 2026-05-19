package dev.novapay.analytics

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class AnalyticsServiceApplication

fun main(args: Array<String>) {
	runApplication<AnalyticsServiceApplication>(*args)
}