package com.syntaric.openfhir.rest;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

public class RequestValidationException extends RuntimeException {
    @Getter
    private List<String> messages;

    public RequestValidationException(String message, List<String> messages) {
        super(message);
        this.messages = messages;
        if(this.messages == null) {
            this.messages = new ArrayList<>();
            this.messages.add(message);
        }
    }
}
