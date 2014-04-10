package controllers;

import models.JsonPayload;

import models.StatusMessage;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.client.exceptions.SynapseUnauthorizedException;

import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Http.Cookie;

@org.springframework.stereotype.Controller
public class BaseController extends Controller {
	
	protected String getSessionToken() throws Exception {
		Cookie cookie = request().cookie(BridgeConstants.SESSION_TOKEN);
		if (cookie == null) {
			throw new SynapseUnauthorizedException();
		}
		// Note that the cookie may not be valid. There's no way to check other than to contact Synapse.
		// If this is part of a bridge call, then we *must* contact Synapse to determine this.
		return cookie.value();
	}
	
	protected Result jsonResult(String message) {
		return ok(Json.toJson(new StatusMessage(message)));
	}
	
	protected Result jsonResult(JsonPayload<?> payload) {
		return ok(Json.toJson(payload));
	}
	
}
