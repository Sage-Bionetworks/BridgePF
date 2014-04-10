package models;

public class ExceptionMessage extends JsonPayload<String> {

	public ExceptionMessage(Throwable throwable) {
		super(throwable.getClass().getSimpleName(), throwable.getMessage());
	}

}
