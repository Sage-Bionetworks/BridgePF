package org.sagebionetworks.bridge.models.schedules;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Activity component representing a task with a task ID and optionally a schema reference. */
public final class TaskReference {
    private final String identifier;
    private final SchemaReference schema;

    /** Simple constructor. */
    @JsonCreator
    public TaskReference(@JsonProperty("identifier") String identifier,
            @JsonProperty("schema") SchemaReference schema) {
        this.identifier = identifier;
        this.schema = schema;
    }

    /** ID associated with this task. */
    public String getIdentifier() {
        return this.identifier;
    }

    /** Schema reference associated with this task, if one exists. */
    public SchemaReference getSchema() {
        return schema;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(identifier, schema);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        TaskReference other = (TaskReference) obj;
        return Objects.equals(identifier, other.identifier) &&
                Objects.equals(schema, other.schema);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "TaskReference [" +
                "identifier=" + identifier +
                ", schema=" + schema +
                "]";
    }
}
