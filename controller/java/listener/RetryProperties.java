package com.example.mqretry;

/**
 * Global retry configuration shared by every listener. Wire a single instance and inject it
 * into all {@link AbstractMqTextListener} subclasses so the values below apply app-wide.
 */
public class RetryProperties {

    /** App-level max retries before a message is dropped (safety cap on JMSXDeliveryCount). */
    private int maxDeliveries = 10;

    /** Upper bound for the exponential backoff sleep, in seconds (1, 2, 4, 8, 10, 10, ...). */
    private int maxBackoffSeconds = 10;

    public int getMaxDeliveries() {
        return maxDeliveries;
    }

    public void setMaxDeliveries(int maxDeliveries) {
        this.maxDeliveries = maxDeliveries;
    }

    public int getMaxBackoffSeconds() {
        return maxBackoffSeconds;
    }

    public void setMaxBackoffSeconds(int maxBackoffSeconds) {
        this.maxBackoffSeconds = maxBackoffSeconds;
    }
}
