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
        // These are guaranteed not null by the Controller. These null checks are just to defend against bad coding.s
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
        getMetadataByIdAllVersions(id);

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

    /** Gets metadata for all versions of all modules. */
    public List<SharedModuleMetadata> getAllMetadataAllVersions() {
        return metadataDao.getAllMetadataAllVersions();
    }

    /** Gets metadata for the latest versions of all modules. */
    public List<SharedModuleMetadata> getAllMetadataLatestVersions() {
        List<SharedModuleMetadata> allMetadataAllVersions = getAllMetadataAllVersions();

        // Map to find latest versions for each metadata by ID.
        Map<String, SharedModuleMetadata> latestMetadataById = new HashMap<>();
        for (SharedModuleMetadata oneMetadata : allMetadataAllVersions) {
            String metadataId = oneMetadata.getId();
            SharedModuleMetadata existing = latestMetadataById.get(metadataId);
            if (existing == null || oneMetadata.getVersion() > existing.getVersion()) {
                latestMetadataById.put(metadataId, oneMetadata);
            }
        }

        // Flatten to a list and return.
        return ImmutableList.copyOf(latestMetadataById.values());
    }

    /** Gets metadata for all versions of the specified module. */
    public List<SharedModuleMetadata> getMetadataByIdAllVersions(String id) {
        if (StringUtils.isBlank(id)) {
            throw new BadRequestException("id must be specified");
        }

        List<SharedModuleMetadata> metadataList = metadataDao.getMetadataByIdAllVersions(id);
        if (metadataList.isEmpty()) {
            throw new EntityNotFoundException(SharedModuleMetadata.class);
        }
        return metadataList;
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
        return metadataDao.getMetadataByIdLatestVersion(id);
    }

    /**
     * <p>
     * Queries module metadata using the given SQL-like WHERE clause. Also filters out any modules that don't contain
     * any of the given tags. If a module has any of these tags (not necessarily all of them), it will be returned in
     * the result.
     * </p>
     * <p>
     * If whereClause is not specified, this method returns all modules. If tags is null or empty, this method does not
     * filter using tags.
     * </p>
     * <p>
     * Example where clause: "published = true AND os = 'iOS'"
     * </p>
     */
    public List<SharedModuleMetadata> queryMetadata(String whereClause, Set<String> tags) {
        // DAO currently only supports where clause
        List<SharedModuleMetadata> metadataList = metadataDao.queryMetadata(whereClause);

        // If tags are specified, we include results that contain any of these tags. (Don't need to match *all* tags.)
        if (tags != null && !tags.isEmpty()) {
            return metadataList.stream().filter(metadata -> !Sets.intersection(metadata.getTags(), tags)
                    .isEmpty()).collect(Collectors.toList());
        } else {
            return metadataList;
        }
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
