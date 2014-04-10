package models;

public class StatusMessage extends JsonPayload<String> {

	public StatusMessage(String message) {
		super("StatusMessage", message);
	}
	
	public StatusMessage(String type, String message) {
		super(type, message);
	}
	
}
