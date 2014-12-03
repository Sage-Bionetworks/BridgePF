package org.sagebionetworks.bridge.models.upload;

import static com.google.common.base.Preconditions.checkNotNull;
import com.fasterxml.jackson.databind.JsonNode;

public class ArchiveEntry {

    private final String name;
    private final JsonNode content;

    public ArchiveEntry(String name, JsonNode content) {
        checkNotNull(name);
        checkNotNull(content);
        this.name = name;
        this.content = content;
    }

    public String getName() {
        return name;
    }

    public JsonNode getContent() {
        return content;
    }
}
