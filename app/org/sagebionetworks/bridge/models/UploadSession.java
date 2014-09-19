package org.sagebionetworks.bridge.models;

import java.net.URL;

public class UploadSession {

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
    public long getExpires() {
        return expires;
    }

    private final String id;
    private final URL url;
    private final long expires;
}
