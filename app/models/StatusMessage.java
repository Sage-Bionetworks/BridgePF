package models;

public class StatusMessage extends JsonPayload<String> {

    public StatusMessage(String message) {
        super(message);
        this.type = "StatusMessage";
    }
    
}
