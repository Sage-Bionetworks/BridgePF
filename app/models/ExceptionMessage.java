package models;

public class ExceptionMessage extends JsonPayload<String> {

	public ExceptionMessage(Throwable throwable) {
		super("Exception", throwable.getMessage());
	}

}
