package org.sagebionetworks.bridge.play.http;

import models.StatusMessage;
import play.http.HttpErrorHandler;
import play.libs.F.Promise;
import play.libs.Json;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;
import play.mvc.Results;

public class BridgeErrorHandler implements HttpErrorHandler {

    @Override
    public Promise<Result> onClientError(RequestHeader request, int statusCode, String message) {
        return getResult(request, message);
    }

    @Override
    public Promise<Result> onServerError(RequestHeader request, Throwable exception) {
        return getResult(request, exception.getMessage());
    }

    private Promise<Result> getResult(final RequestHeader request, final String message) {
        return Promise.<Result>pure(Results.badRequest(
                views.html.defaultpages.badRequest.render(request.method(), request.uri(), 
                        Json.toJson(new StatusMessage(message)).asText())));
    }
}
