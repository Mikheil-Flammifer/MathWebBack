package com.mathweb.exception;

import org.springframework.http.HttpStatus;

public class SubscriptionException extends AppException {

    public SubscriptionException(String message) {
        super(message, HttpStatus.PAYMENT_REQUIRED);
    }
}