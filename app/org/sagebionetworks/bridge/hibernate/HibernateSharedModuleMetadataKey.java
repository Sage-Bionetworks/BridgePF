package org.sagebionetworks.bridge.hibernate;

import java.io.Serializable;
import java.util.Objects;

import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleMetadata;

/**
 * In order to use composite primary keys, Hibernate requires us to make a separate class representing that key. That
 * class needs to have a no-args constructor, be Serializable, and have hashCode() and equals() defined. This is that
 * class. It encapsulates module ID and version.
 */
@SuppressWarnings("serial")
public final class HibernateSharedModuleMetadataKey implements Serializable {
    private String id;
    private int version;

    /** No-args constructor, as required by Hibernate. */
    @SuppressWarnings("unused")
    public HibernateSharedModuleMetadataKey() {
    }

    /** Convenience method for quickly creating a key with both ID and version. */
    public HibernateSharedModuleMetadataKey(String id, int version) {
        this.id = id;
        this.version = version;
    }

    /** @see SharedModuleMetadata#getId */
    public String getId() {
        return id;
    }

    /** @see SharedModuleMetadata#getId */
    public void setId(String id) {
        this.id = id;
    }

    /** @see SharedModuleMetadata#getVersion */
    public int getVersion() {
        return version;
    }

    /** @see SharedModuleMetadata#getVersion */
    public void setVersion(int version) {
        this.version = version;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HibernateSharedModuleMetadataKey)) {
            return false;
        }
        HibernateSharedModuleMetadataKey that = (HibernateSharedModuleMetadataKey) o;
        return version == that.version &&
                Objects.equals(id, that.id);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(id, version);
    }
}
