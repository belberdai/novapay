package dev.novapay.payments.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.Map;

/**
 * Thin wrapper around AWS SNS for publishing outbox events.
 */
@Component
public class SnsEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SnsEventPublisher.class);

    private final SnsClient snsClient;
    private final String topicArn;

    public SnsEventPublisher(SnsClient snsClient,
                             @Value("${aws.sns.payment-events-topic-arn}") String topicArn) {
        this.snsClient = snsClient;
        this.topicArn = topicArn;
    }

    /**
     * Publish a single event payload to the configured SNS topic.
     * Throws if SNS rejects; the caller's transaction will roll back.
     * <p>
     * The event_type goes in a MessageAttribute so consumers can filter via
     * SNS subscription filter policies without parsing the body.
     */
    public void publish(OutboxEvent event) {
        PublishRequest request = PublishRequest.builder()
                .topicArn(topicArn)
                .message(event.getPayload())
                .messageAttributes(Map.of(
                        "event_type", MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(event.getEventType())
                                .build(),
                        "aggregate_type", MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(event.getAggregateType())
                                .build()
                ))
                .build();

        PublishResponse response = snsClient.publish(request);
        log.debug("Published event {} (outbox id={}) — SNS message id={}",
                event.getEventType(), event.getId(), response.messageId());
    }
}