package com.mathweb.exception;

import org.springframework.http.HttpStatus;

public class OtpException extends AppException {

    public OtpException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}