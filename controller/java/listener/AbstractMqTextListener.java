package com.example.mqretry;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractMqTextListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(AbstractMqTextListener.class);
    private static final String DELIVERY_COUNT = "JMSXDeliveryCount";

    private final JmsErrorClassifier classifier;
    private final RetryProperties properties;

    protected AbstractMqTextListener(JmsErrorClassifier classifier, RetryProperties properties) {
        this.classifier = classifier;
        this.properties = properties;
    }

    protected abstract void handle(TextMessage message, String payload) throws Exception;

    @Override
    public final void onMessage(Message message) {
        if (!(message instanceof TextMessage)) {
            log.error("Dropping non-text message: id={}, type={}",
                    messageId(message), message == null ? "null" : message.getClass().getName());
            return;
        }

        TextMessage textMessage = (TextMessage) message;
        int unknownAttempts = 0;

        while (true) {
            String payload = null;
            try {
                payload = textMessage.getText();
                handle(textMessage, payload);
                return;
            } catch (Throwable ex) {
                RetryDecision decision = classifier.classify(ex);

                if (decision == RetryDecision.TRANSIENT) {
                    requeueOrDrop(textMessage, payload, ex);
                    return;
                }

                if (decision == RetryDecision.NON_RECOVERABLE) {
                    log.error("Dropping non-recoverable message: id={}, payload={}",
                            messageId(textMessage), payload, ex);
                    return;
                }

                unknownAttempts++;
                if (unknownAttempts >= properties.getUnknownMaxAttempts()) {
                    log.error("Dropping message after {} unknown-error attempts: id={}, payload={}",
                            unknownAttempts, messageId(textMessage), payload, ex);
                    return;
                }
                log.warn("Unknown error, in-thread retry {}/{}: id={}",
                        unknownAttempts, properties.getUnknownMaxAttempts(), messageId(textMessage), ex);
            }
        }
    }

    private void requeueOrDrop(TextMessage message, String payload, Throwable ex) {
        int delivery = deliveryCount(message);
        if (delivery >= properties.getTransientMaxDeliveries()) {
            log.error("Dropping transient message at safety limit (delivery={}, max={}): id={}, payload={}",
                    delivery, properties.getTransientMaxDeliveries(), messageId(message), payload, ex);
            return;
        }
        long delay = backoffMillis(delivery);
        log.warn("Transient error, requeue after {}ms (delivery={}): id={}",
                delay, delivery, messageId(message), ex);
        sleep(delay);
        throw new RequeueException("Transient failure, forcing redelivery", ex);
    }

    private long backoffMillis(int delivery) {
        int exponent = Math.max(0, delivery - 1);
        double delay = properties.getInitialDelayMs() * Math.pow(properties.getMultiplier(), exponent);
        return (long) Math.min(delay, properties.getMaxDelayMs());
    }

    private int deliveryCount(Message message) {
        try {
            if (message.propertyExists(DELIVERY_COUNT)) {
                return message.getIntProperty(DELIVERY_COUNT);
            }
        } catch (JMSException ignored) {
        }
        return 1;
    }

    private String messageId(Message message) {
        try {
            return message == null ? "null" : message.getJMSMessageID();
        } catch (JMSException e) {
            return "unknown";
        }
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
