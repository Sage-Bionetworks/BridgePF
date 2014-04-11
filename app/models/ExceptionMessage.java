package models;

public class ExceptionMessage extends JsonPayload<String> {

	public ExceptionMessage(Throwable throwable) {
		super(throwable.getMessage());
		this.type = throwable.getClass().getName();
	}

	public ExceptionMessage(Throwable throwable, String message) {
		super(message);
		this.type = throwable.getClass().getName();
	}
	
}
