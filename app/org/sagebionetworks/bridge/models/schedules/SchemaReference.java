package org.sagebionetworks.bridge.models.schedules;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.sagebionetworks.bridge.models.upload.UploadSchema;

/**
 * Participant-facing schema reference, to be used in scheduled activities. This contains the schema ID and optionally
 * the revision. It does not contain study ID as that's not exposed to participants.
 */
public final class SchemaReference {
    private final String id;
    private final Integer revision;

    /** Constructor. */
    public SchemaReference(@JsonProperty("id") String id, @JsonProperty("revision") Integer revision) {
        this.id = id;
        this.revision = revision;
    }

    /** Schema ID, see {@link UploadSchema#getSchemaId}. */
    public String getId() {
        return id;
    }

    /** Optional schema revision, see {@link UploadSchema#getRevision}. */
    public Integer getRevision() {
        return revision;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SchemaReference that = (SchemaReference) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(revision, that.revision);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(id, revision);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "SchemaReference{" +
                "id='" + id + '\'' +
                ", revision=" + revision +
                '}';
    }
}
