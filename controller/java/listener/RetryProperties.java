package com.example.mqretry;

public class RetryProperties {

    private long initialDelayMs = 1000L;
    private double multiplier = 2.0;
    private long maxDelayMs = 30000L;
    private int transientMaxDeliveries = 10;
    private int unknownMaxAttempts = 3;

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public void setInitialDelayMs(long initialDelayMs) {
        this.initialDelayMs = initialDelayMs;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    public void setMaxDelayMs(long maxDelayMs) {
        this.maxDelayMs = maxDelayMs;
    }

    public int getTransientMaxDeliveries() {
        return transientMaxDeliveries;
    }

    public void setTransientMaxDeliveries(int transientMaxDeliveries) {
        this.transientMaxDeliveries = transientMaxDeliveries;
    }

    public int getUnknownMaxAttempts() {
        return unknownMaxAttempts;
    }

    public void setUnknownMaxAttempts(int unknownMaxAttempts) {
        this.unknownMaxAttempts = unknownMaxAttempts;
    }
}
