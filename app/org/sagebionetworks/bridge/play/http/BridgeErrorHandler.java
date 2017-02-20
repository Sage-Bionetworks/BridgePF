package org.sagebionetworks.bridge.play.http;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import play.http.HttpErrorHandler;
import play.libs.F.Promise;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;
import play.mvc.Results;

public class BridgeErrorHandler implements HttpErrorHandler {

    @Override
    public Promise<Result> onClientError(RequestHeader request, int statusCode, String message) {
        String type = (statusCode == 404) ? "EndpointNotFoundException" : "BridgeServiceException";
        String finalMessage = (statusCode == 404) ? "Endpoint not found." : message;
        return getResult(request, statusCode, type, finalMessage);
    }

    @Override
    public Promise<Result> onServerError(RequestHeader request, Throwable exception) {
        int statusCode = (exception instanceof BridgeServiceException) ?
                ((BridgeServiceException)exception).getStatusCode() : 500;
        return getResult(request, statusCode, BridgeUtils.getTypeName(exception.getClass()), exception.getMessage());
    }

    private Promise<Result> getResult(final RequestHeader request, final int statusCode, final String excType, final String message) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("statusCode", statusCode);
        node.put("message", message);
        node.put("type", excType);
        Result result = Results.status(statusCode, node).as(BridgeConstants.JSON_MIME_TYPE);
        return Promise.<Result>pure(result);
    }
}
