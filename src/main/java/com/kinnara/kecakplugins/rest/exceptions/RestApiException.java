package com.kinnara.kecakplugins.rest.exceptions;

/**
 * @author aristo
 *
 * Rest API Exception
 */
public class RestApiException extends Exception {
    private int errorCode;

    public RestApiException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public final int getErrorCode() {
        return errorCode;
    }
}
