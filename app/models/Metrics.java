package models;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.sagebionetworks.bridge.json.DateUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Request-scoped metrics.
 */
public class Metrics {

    /** The version of the metrics schema. */
    private static final int VERSION = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ObjectNode json;

    public static String getCacheKey(String requestId) {
        checkArgument(isNotBlank(requestId), "Request ID cannot be blank.");
        return requestId + ":" + Metrics.class.getSimpleName();
    }

    public Metrics(final String requestId) {
        json = MAPPER.createObjectNode();
        json.put("version", VERSION);
        start();
        setRequestId(requestId);
    }

    public String getCacheKey() {
        return Metrics.getCacheKey(json.get("request_id").asText());
    }

    public String toJsonString() {
        return json.toString();
    }

    public void start() {
        json.put("start", DateUtils.getCurrentISODateTime());
    }

    public void end() {
        json.put("end", DateUtils.getCurrentISODateTime());
    }

    public void setRequestId(String requestId) {
        checkArgument(isNotBlank(requestId), "Request ID cannot be blank.");
        json.put("request_id", requestId);
    }

    public void setRemoteAddress(String remoteAddress) {
        put("remote_address", remoteAddress);
    }

    public void setMethod(String method) {
        put("method", method);
    }

    public void setUri(String uri) {
        put("uri", uri);
    }

    public void setProtocol(String protocol) {
        put("protocol", protocol);
    }

    public void setUserAgent(String userAgent) {
        put("user_agent", userAgent);
    }

    public void setStatus(int status) {
        json.put("status", status);
    }

    public void setStudy(String study) {
        put("study", study);
    }

    public void setUserId(String userId) {
        put("user_id", userId);
    }

    public void setSessionId(String sessionId) {
        put("session_id", sessionId);
    }

    public void setSpToken(String spToken) {
        put("sp_token", spToken);
    }

    public void setUploadId(String uploadId) {
        put("upload_id", uploadId);
    }

    public void setUploadSize(long uploadSize) {
        json.put("upload_size", uploadSize);
    }

    public void setSharingOption(String sharingOption) {
        put("sharing_option", sharingOption);
    }

    private void put(final String field, final String value) {
        if (isNotBlank(value)) {
            json.put(field, value);
        }
    }
}
