package com.example.adapter.exception;

/**
 * Thrown when a request is made but the backing git repository
 * has not been successfully cloned/opened yet.
 */
public class RepositoryNotInitializedException extends RuntimeException {

    public RepositoryNotInitializedException(String message) {
        super(message);
    }

    public RepositoryNotInitializedException(String message, Throwable cause) {
        super(message, cause);
    }
}
