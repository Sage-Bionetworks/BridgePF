package org.sagebionetworks.bridge.models.upload;

import java.net.URL;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@BridgeTypeName("UploadSession")
public class UploadSession implements BridgeEntity {

    public UploadSession(String id, URL url, long expires) {
        this.id = id;
        this.url = url;
        this.expires = expires;
    }

    public String getId() {
        return id;
    }

    public String getUrl() {
        return url.toString();
    }

    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public long getExpires() {
        return expires;
    }

    private final String id;
    private final URL url;
    private final long expires;
}
