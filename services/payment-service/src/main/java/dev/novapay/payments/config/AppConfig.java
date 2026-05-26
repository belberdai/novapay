package dev.novapay.payments.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.SnsClientBuilder;

import java.net.URI;
import java.time.Clock;

@Configuration
public class AppConfig {

    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    SnsClient snsClient(
            @Value("${aws.sns.endpoint:}") String endpoint,
            @Value("${aws.region:us-east-1}") String region,
            @Value("${aws.access-key-id:}") String accessKey,
            @Value("${aws.secret-access-key:}") String secretKey) {

        SnsClientBuilder builder = SnsClient.builder()
                .region(Region.of(region));

        // LocalStack: explicit static credentials + endpoint override.
        // Real AWS: DefaultCredentialsProvider picks up the ECS task role
        // via the container credentials endpoint automatically.
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(
                                    accessKey.isBlank() ? "test" : accessKey,
                                    secretKey.isBlank() ? "test" : secretKey)));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }
}