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
            log.error("Dropping message at delivery safety limit (delivery={}, max={}, decision={}): id={}, payload={}",
                    delivery, properties.getMaxDeliveries(), decision, messageId(message), payload, ex);
            return;
        }
        log.warn("{} error, letting IBM MQ redeliver (delivery={}): id={}",
                decision, delivery, messageId(message), ex);
        throw new RequeueException("Recoverable failure, forcing IBM MQ redelivery", ex);
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
