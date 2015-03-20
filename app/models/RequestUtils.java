package models;

import static org.sagebionetworks.bridge.BridgeConstants.X_REQUEST_ID_HEADER;
import play.mvc.Http.Request;

public final class RequestUtils {

    private RequestUtils() {
    }

    public static String getRequestId(final Request request) {
        return header(request, X_REQUEST_ID_HEADER,
                Integer.toHexString(System.identityHashCode(request)));
    }

    public static String header(final Request request, final String name, final String defaultVal) {
        final String[] values = request.headers().get(name);
        return (values != null && values.length > 0) ? values[0] : defaultVal;
    }
}
