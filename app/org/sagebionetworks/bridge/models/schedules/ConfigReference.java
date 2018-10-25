package org.sagebionetworks.bridge.models.schedules;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class ConfigReference {
    private final String id;
    private final Long revision;

    @JsonCreator
    public ConfigReference(@JsonProperty("id") String id, @JsonProperty("revision") Long revision) {
        this.id = id;
        this.revision = revision;
    }
    public String getId() {
        return id;
    }
    public Long getRevision() {
        return revision;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConfigReference that = (ConfigReference) o;
        return Objects.equals(id, that.id) && Objects.equals(revision, that.revision);
    }
    @Override
    public int hashCode() {
        return Objects.hash(id, revision);
    }
}
