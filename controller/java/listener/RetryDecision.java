package com.example.mqretry;

public enum RetryDecision {
    TRANSIENT,
    NON_RECOVERABLE,
    UNKNOWN
}
