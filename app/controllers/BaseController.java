package controllers;

import models.JsonPayload;
import models.StatusMessage;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.client.exceptions.SynapseUnauthorizedException;

import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http.Cookie;
import play.mvc.Result;

@org.springframework.stereotype.Controller
public class BaseController extends Controller {

	protected String getSessionToken() throws Exception {
		Cookie sessionCookie = request().cookie(BridgeConstants.SESSION_TOKEN);
		if (sessionCookie != null && sessionCookie.value() != null) {
			return sessionCookie.value();
		}
		String[] session = request().headers().get(BridgeConstants.SESSION_TOKEN);
		if (session == null || session.length == 0) {
			throw new SynapseUnauthorizedException();
		}
		return session[0];
	}

	protected Result jsonResult(String message) {
		return ok(Json.toJson(new StatusMessage(message)));
	}

	protected Result jsonResult(JsonPayload<?> payload) {
		return ok(Json.toJson(payload));
	}

}
