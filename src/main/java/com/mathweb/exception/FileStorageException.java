package com.mathweb.exception;

import org.springframework.http.HttpStatus;

public class FileStorageException extends AppException {

    public FileStorageException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}