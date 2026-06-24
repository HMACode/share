package com.example.mqretry;

public class RetryProperties {

    private int maxDeliveries = 10;

    public int getMaxDeliveries() {
        return maxDeliveries;
    }

    public void setMaxDeliveries(int maxDeliveries) {
        this.maxDeliveries = maxDeliveries;
    }
}
