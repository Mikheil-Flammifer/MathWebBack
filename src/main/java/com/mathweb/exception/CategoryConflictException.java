package com.mathweb.exception;

/** Thrown when a category operation conflicts with existing data. Mapped to HTTP 409. */
public class CategoryConflictException extends RuntimeException {

    public CategoryConflictException(String message) {
        super(message);
    }
}