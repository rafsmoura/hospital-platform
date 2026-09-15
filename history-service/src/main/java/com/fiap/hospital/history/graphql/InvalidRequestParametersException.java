package com.fiap.hospital.history.graphql;

public class InvalidRequestParametersException extends RuntimeException {

    public InvalidRequestParametersException(Throwable cause) {
        super("Invalid request parameters", cause);
    }
}
