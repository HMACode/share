package com.example.mqretry;

public class RequeueException extends RuntimeException {

    public RequeueException(String message, Throwable cause) {
        super(message, cause);
    }
}
