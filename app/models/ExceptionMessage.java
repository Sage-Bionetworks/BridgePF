package models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class ExceptionMessage {

    /**
     * When the user has not consented to research, they are not logged in, 
     * but they are issued a session token to make the agreement. So that
     * session is passed back as part of the exception JSON. Otherwise, this
     * is not used.
     */
    //private String sessionToken;
    private final String message;
    
    public ExceptionMessage(Throwable throwable) {
        this.message = throwable.getMessage();
    }

    public ExceptionMessage(Throwable throwable, String message) {
        this.message = message;
    }
    
    /*
    public ExceptionMessage(Throwable throwable, String message, String sessionToken) {
        this.message = message;
        this.sessionToken = sessionToken;
    }
    */
    
    public String getMessage() {
        return message;
    }
    /*
    public String getSessionToken() {
        return sessionToken;
    }
     */
}
