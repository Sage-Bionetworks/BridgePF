package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.SharedModuleMetadataDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleMetadata;
import org.sagebionetworks.bridge.validators.SharedModuleMetadataValidator;
import org.sagebionetworks.bridge.validators.Validate;

/** Service for Shared Module Metadata. */
@Component
public class SharedModuleMetadataService {
    private static final Logger LOG = LoggerFactory.getLogger(SharedModuleMetadataService.class);

    private SharedModuleMetadataDao metadataDao;

    /** Shared Module Metadata DAO, configured by Spring. */
    @Autowired
    public final void setMetadataDao(SharedModuleMetadataDao metadataDao) {
        this.metadataDao = metadataDao;
    }

    /**
     * Creates the specified metadata object. Gets the module ID from the metadata object. If the module version is not
     * specified, it will auto-increment one from the most recent version. Throws BadRequestException if the module ID
     * is not specified. Throws InvalidEntityException if the module metadata is invalid.
     */
    public SharedModuleMetadata createMetadata(SharedModuleMetadata metadata) {
        // These are guaranteed not null by the Controller. These null checks are just to defend against bad coding.
        checkNotNull(metadata, "metadata must be non-null");

        // Module ID is validated by getLatestMetadataVersion()

        // Set version if needed. 0 represents an unset module version
        int oldVersion = getLatestMetadataVersion(metadata.getId());
        if (metadata.getVersion() == 0) {
            metadata.setVersion(oldVersion + 1);
        }

        // validate metadata
        Validate.entityThrowingException(SharedModuleMetadataValidator.INSTANCE, metadata);

        // call through to DAO
        return metadataDao.createMetadata(metadata);
    }

    // Helper method to get the latest version of the given module. Returns 0 if there are no modules.
    private int getLatestMetadataVersion(String id) {
        // getLatestNoThrow() will return null instead of throwing EntityNotFoundException
        // However, it will still validate the input and throw BadRequestException.
        SharedModuleMetadata metadata = getLatestNoThrow(id);
        if (metadata != null) {
            return metadata.getVersion();
        } else {
            return 0;
        }
    }

    /**
     * Deletes all metadata for module versions with the given ID. Throws EntityNotFoundException if there are no such
     * module versions.
     */
    public void deleteMetadataByIdAllVersions(String id) {
        // Check that module exists. Module ID is validated by get().
        getMetadataByIdLatestVersion(id);

        metadataDao.deleteMetadataByIdAllVersions(id);
    }

    /**
     * Deletes metadata for the specified module ID and version. Throws EntityNotFoundException if that module version
     * doesn't exist.
     */
    public void deleteMetadataByIdAndVersion(String id, int version) {
        // Check that module exists. Module ID and version is validated by get().
        getMetadataByIdAndVersion(id, version);

        metadataDao.deleteMetadataByIdAndVersion(id, version);
    }

    /** Gets metadata for the specified version of the specified module. */
    public SharedModuleMetadata getMetadataByIdAndVersion(String id, int version) {
        if (StringUtils.isBlank(id)) {
            throw new BadRequestException("id must be specified");
        }
        if (version <= 0) {
            throw new BadRequestException("version must be positive");
        }

        SharedModuleMetadata metadata = metadataDao.getMetadataByIdAndVersion(id, version);
        if (metadata == null) {
            throw new EntityNotFoundException(SharedModuleMetadata.class);
        }
        return metadata;
    }

    /** Gets metadata for the latest version of the specified module. */
    public SharedModuleMetadata getMetadataByIdLatestVersion(String id) {
        SharedModuleMetadata metadata = getLatestNoThrow(id);
        if (metadata == null) {
            throw new EntityNotFoundException(SharedModuleMetadata.class);
        }
        return metadata;
    }

    // Helper method to get the latest metadata, but doesn't throw EntityNotFoundException if it doesn't exist. Note,
    // it still validates the input and will throw a BadRequestException.
    private SharedModuleMetadata getLatestNoThrow(String id) {
        if (StringUtils.isBlank(id)) {
            throw new BadRequestException("id must be specified");
        }
        List<SharedModuleMetadata> queryMetadataList = queryMetadataById(id, true, false, null, null);

        if (queryMetadataList.isEmpty()) {
            // If there are no results, return null instead of throwing.
            return null;
        }

        if (queryMetadataList.size() > 1) {
            // Error, because this represents a coding error that we should fix.
            LOG.error("Most recent by ID " + id + " returned more than one result");
        }

        // Return the first (only) result.
        return queryMetadataList.get(0);
    }

    /**
     * <p>
     * Queries module metadata using the set of given parameters. Can filter on most recent version of a module,
     * published modules, a SQL-like WHERE clause, and tags.
     * </p>
     * <p>
     * Internally, the where clause will be applied first. If published=true, this will add another clause to the
     * where in the SQL query. Alternatively, if published=false, this will look at both published and unpublished
     * module versions. If specified, tags will be applied last. If a module has any of these tags (not necessarily all
     * of them), it will be returned in the result.
     * </p>
     * <p>
     * mostrecent is a special case. If mostrecent=true, then we return the most recent versions. If mostrecent=true
     * and published=true, we return the most recent published versions. Tags can be used on top of this.
     * mostrecent=true can't be specified at the same time as a where clause.
     * </p>
     * <p>
     * Example where clause: "published = true AND os = 'iOS'"
     * </p>
     */
    public List<SharedModuleMetadata> queryAllMetadata(boolean mostRecent, boolean published, String where,
            Set<String> tags) {
        boolean hasWhere = StringUtils.isNotBlank(where);
        String whereInternal = null;
        if (mostRecent) {
            if (hasWhere) {
                // This is disallowed because of the confusion (both from Bridge developers and from Study managers) on
                // how this would actually work.
                throw new BadRequestException("mostrecent=true cannot be specified with where clause");
            }

            if (published) {
                // Most recent published version is a special case. This is implemented by first querying for published
                // versions, then filtering for most recent.
                whereInternal = "published=true";
            }
        } else {
            if (published && hasWhere) {
                // Published and where both contribute to the query.
                whereInternal = "published=true AND " + where;
            } else if (published) {
                whereInternal = "published=true";
            } else if (hasWhere) {
                whereInternal = where;
            }
        }

        // Run actual query.
        List<SharedModuleMetadata> metadataList = metadataDao.queryMetadata(whereInternal);

        // Map to find latest versions for each metadata by ID. This is applied before tags.
        if (mostRecent) {
            Map<String, SharedModuleMetadata> latestMetadataById = new HashMap<>();
            for (SharedModuleMetadata oneMetadata : metadataList) {
                String metadataId = oneMetadata.getId();
                SharedModuleMetadata existing = latestMetadataById.get(metadataId);
                if (existing == null || oneMetadata.getVersion() > existing.getVersion()) {
                    latestMetadataById.put(metadataId, oneMetadata);
                }
            }

            metadataList = ImmutableList.copyOf(latestMetadataById.values());
        }

        // If tags are specified, we include results that contain any of these tags. (Don't need to match *all* tags.)
        if (tags != null && !tags.isEmpty()) {
            return metadataList.stream().filter(metadata -> !Sets.intersection(metadata.getTags(), tags)
                    .isEmpty()).collect(Collectors.toList());
        } else {
            return metadataList;
        }
    }

    /** Similar to queryAllMetadata, except this only queries on module versions of the specified ID. */
    public List<SharedModuleMetadata> queryMetadataById(String id, boolean mostRecent, boolean published, String where,
            Set<String> tags) {
        if (StringUtils.isBlank(id)) {
            throw new BadRequestException("id must be specified");
        }

        // Query all metadata and just filter by ID.
        List<SharedModuleMetadata> queryAllMetadataList = queryAllMetadata(mostRecent, published, where, tags);
        return queryAllMetadataList.stream().filter(metadata -> id.equals(metadata.getId())).collect(Collectors
                .toList());
    }

    /**
     * Updates metadata for the specified module version. If the module version doesn't exist, this throws a
     * EntityNotFoundException.
     */
    public SharedModuleMetadata updateMetadata(String id, int version, SharedModuleMetadata metadata) {
        // Check that module exists. Module ID and version are validated by get().
        getMetadataByIdAndVersion(id, version);

        // Set id and version on the metadata object, to verify we are setting the correct module version.
        metadata.setId(id);
        metadata.setVersion(version);

        // validate metadata
        Validate.entityThrowingException(SharedModuleMetadataValidator.INSTANCE, metadata);

        // call through to DAO
        return metadataDao.updateMetadata(metadata);
    }
}
