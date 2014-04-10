package models;

/**
 * Intended to provide a consistent JSON structure so the client code has a way
 * to interrogate responses and determine what their content is. For example, 
 * errors and data will come back with the same top-level structure.
 *
 * @param <T>
 */
public class JsonPayload<T> {

	private String type;
	private T payload;
	
	public JsonPayload(String type, T payload) {
		this.type = type;
		this.payload = payload;
	}
	
	public JsonPayload(T payload) {
		this(payload.getClass().getSimpleName(), payload);
	}
	
	public String getType() {
		return type;
	}
	
	public T getPayload() {
		return payload;
	}

}
