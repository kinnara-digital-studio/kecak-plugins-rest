package com.kinnara.kecakplugins.rest.exceptions;

/**
 * @author aristo
 *
 * Rest Client Exception
 */
public class RestClientException extends Exception{
    public RestClientException(Throwable cause) {
        super(cause);
    }

    public RestClientException(String message) {
        super(message);
    }
}
