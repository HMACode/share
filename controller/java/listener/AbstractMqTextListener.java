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
        String payload = null;
        try {
            payload = textMessage.getText();
            handle(textMessage, payload);
        } catch (Throwable ex) {
            RetryDecision decision = classifier.classify(ex);

            if (decision == RetryDecision.NON_RECOVERABLE) {
                log.error("Dropping non-recoverable message: id={}, payload={}",
                        messageId(textMessage), payload, ex);
                return;
            }

            requeueOrDrop(textMessage, payload, decision, ex);
        }
    }

    private void requeueOrDrop(TextMessage message, String payload, RetryDecision decision, Throwable ex) {
        int delivery = deliveryCount(message);
        if (delivery >= properties.getMaxDeliveries()) {
            log.error("Dropping message at retry limit (delivery={}, max={}, decision={}): id={}, payload={}",
                    delivery, properties.getMaxDeliveries(), decision, messageId(message), payload, ex);
            return;
        }
        long delay = backoffMillis(delivery);
        log.warn("{} error, sleeping {}ms then letting IBM MQ redeliver (delivery={}): id={}",
                decision, delay, delivery, messageId(message), ex);
        sleep(delay);
        throw new RequeueException("Recoverable failure, forcing IBM MQ redelivery", ex);
    }

    /** Exponential backoff: 1, 2, 4, 8 seconds, then capped at maxBackoffSeconds (1, 2, 4, 8, 10, 10, ...). */
    private long backoffMillis(int delivery) {
        int exponent = Math.max(0, delivery - 1);
        long seconds = exponent >= 31 ? properties.getMaxBackoffSeconds()
                : Math.min(1L << exponent, properties.getMaxBackoffSeconds());
        return seconds * 1000L;
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
}
