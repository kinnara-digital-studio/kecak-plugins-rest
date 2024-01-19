package com.kinnarastudio.kecakplugins.rest.exceptions;

/**
 * @author aristo
 *
 * Rest Client Exception
 */
public class RestClientException extends Exception {
    public RestClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public RestClientException(Throwable cause) {
        super(cause);
    }

    public RestClientException(String message) {
        super(message);
    }
}
