package org.sagebionetworks.bridge.models;

import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class ExceptionMessage {

    private final String message;
    private final Map<String,List<String>> errors;
    
    public ExceptionMessage(Throwable throwable, String message) {
        this.message = (message != null) ? message : throwable.getMessage();
        if (throwable instanceof InvalidEntityException) {
            this.errors = ((InvalidEntityException)throwable).getErrors();    
        } else {
            this.errors = null;
        }
    }
    
    public String getMessage() {
        return message;
    }
    
    public Map<String,List<String>> getErrors() {
        return errors;
    }
    
}
