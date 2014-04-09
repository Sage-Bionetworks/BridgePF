package controllers;

import org.apache.commons.lang3.StringUtils;

import global.StatusMessage;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

public class BaseController extends Controller {

	protected Result jsonMessage(int code, String message) {
		if (code == 0) {
			throw new IllegalArgumentException("Invalid status code: 0");
		}
		if (StringUtils.isBlank(message)) {
			throw new IllegalArgumentException("Invalid status message");
		}
		return ok(Json.toJson(new StatusMessage(code, message)));
	}
	
	protected Result jsonError(int code, String message) {
		if (code == 0) {
			throw new IllegalArgumentException("Invalid status code: 0");
		}
		if (StringUtils.isBlank(message)) {
			throw new IllegalArgumentException("Invalid status message");
		}
		return status(code, Json.toJson(new StatusMessage(code, message)));
	}
	
	
}
