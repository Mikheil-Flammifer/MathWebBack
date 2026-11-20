package com.mathweb.exception;

public class PaymentIdentityConflictException extends RuntimeException {
    public PaymentIdentityConflictException(String message) {
        super(message);
    }
}