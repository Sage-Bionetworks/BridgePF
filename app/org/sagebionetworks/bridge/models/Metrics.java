package org.sagebionetworks.bridge.models;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Request-scope metrics.
 */
public class Metrics {

    private static final Logger logger = LoggerFactory.getLogger(Metrics.class);

    /** The version of the metrics schema. */
    private static final int VERSION = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ObjectNode json;

    public static String getCacheKey(String requestId) {
        checkArgument(isNotBlank(requestId), "Request ID cannot be blank.");
        return requestId + ":" + Metrics.class.getSimpleName();
    }

    public Metrics() {
        json = MAPPER.createObjectNode();
        json.put("version", VERSION);
    }

    public String toJsonString() {
        try {
            return MAPPER.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            final String msg = "Failed to write metrics.";
            logger.error(msg, e);
            return msg;
        }
    }

    public void start() {
        json.put("start", DateTime.now(DateTimeZone.UTC).toString(ISODateTimeFormat.dateTime()));
    }

    public void end() {
        json.put("end", DateTime.now(DateTimeZone.UTC).toString(ISODateTimeFormat.dateTime()));
    }

    public void setRequestId(String requestId) {
        put("request_id", requestId);
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

    public void setSpToken(String spToken) {
        put("sp_token", spToken);
    }

    public void setUploadSize(String uploadSize) {
        put("upload_size", uploadSize);
    }

    public void setSharingOption(String sharingOption) {
        put("sharing_option", sharingOption);
    }

    private void put(final String field, final String value) {
        if (StringUtils.isNotBlank(value)) {
            json.put(field, value);
        }
    }
}
