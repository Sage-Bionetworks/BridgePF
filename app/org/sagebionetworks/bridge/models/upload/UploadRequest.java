package org.sagebionetworks.bridge.models.upload;

import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.JsonNode;

public class UploadRequest implements BridgeEntity {

    private static final String NAME_FIELD = "name";
    private static final String LENGTH_FIELD = "contentLength";
    private static final String MD5_FIELD = "contentMd5";
    private static final String TYPE_FIELD = "contentType";

    public static final UploadRequest fromJson(JsonNode node) {
        UploadRequest upload = new UploadRequest();
        upload.name = JsonUtils.asText(node, NAME_FIELD);
        upload.contentLength = JsonUtils.asLongPrimitive(node, LENGTH_FIELD);
        upload.contentMd5 = JsonUtils.asText(node, MD5_FIELD);
        upload.contentType = JsonUtils.asText(node, TYPE_FIELD);
        return upload;
    }

    public String getName() {
        return name;
    }

    public long getContentLength() {
        return contentLength;
    }

    public String getContentMd5() {
        return contentMd5;
    }

    public String getContentType() {
        return contentType;
    }

    private String name;
    private long contentLength;
    private String contentMd5;
    private String contentType;
}
