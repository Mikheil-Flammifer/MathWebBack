package com.mathweb.exception;

import org.springframework.http.HttpStatus;

public class PaymentProviderException extends AppException {

    public PaymentProviderException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }

    public PaymentProviderException(String message, Throwable cause) {
        super(message, HttpStatus.BAD_GATEWAY);
        initCause(cause);
    }
}