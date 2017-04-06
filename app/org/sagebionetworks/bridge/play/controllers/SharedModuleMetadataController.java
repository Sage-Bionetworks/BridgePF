package org.sagebionetworks.bridge.play.controllers;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import play.mvc.Result;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleMetadata;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.SharedModuleMetadataService;

/** Play controller for Shared Module Metadata. */
@Controller
public class SharedModuleMetadataController extends BaseController {
    private SharedModuleMetadataService metadataService;

    /** Shared Module Metadata Service, configured by Spring. */
    @Autowired
    public final void setMetadataService(SharedModuleMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    /** Creates the specified metadata object. */
    public Result createMetadata() throws Exception {
        verifySharedDeveloperAccess();
        SharedModuleMetadata metadata = parseJson(request(), SharedModuleMetadata.class);
        SharedModuleMetadata createdMetadata = metadataService.createMetadata(metadata);
        return createdResult(createdMetadata);
    }

    /** Deletes all metadata for module versions with the given ID. */
    public Result deleteMetadataByIdAllVersions(String id) {
        verifySharedDeveloperAccess();
        metadataService.deleteMetadataByIdAllVersions(id);
        return okResult("Metadata has been deleted.");
    }

    /** Deletes metadata for the specified module ID and version. */
    public Result deleteMetadataByIdAndVersion(String id, int version) {
        verifySharedDeveloperAccess();
        metadataService.deleteMetadataByIdAndVersion(id, version);
        return okResult("Metadata has been deleted.");
    }

    /** Gets metadata for the specified version of the specified module. */
    public Result getMetadataByIdAndVersion(String id, int version) {
        getAuthenticatedSession(Roles.DEVELOPER);
        SharedModuleMetadata metadata = metadataService.getMetadataByIdAndVersion(id, version);
        return okResult(metadata);
    }

    /** Gets metadata for the latest version of the specified module. */
    public Result getMetadataByIdLatestVersion(String id) {
        getAuthenticatedSession(Roles.DEVELOPER);
        SharedModuleMetadata metadata = metadataService.getMetadataByIdLatestVersion(id);
        return okResult(metadata);
    }

    /**
     * Queries module metadata using the set of given parameters. See
     * {@link SharedModuleMetadataService#queryAllMetadata} for details.
     */
    public Result queryAllMetadata(String mostRecentString, String publishedString, String where, String tagsString) {
        getAuthenticatedSession(Roles.DEVELOPER);

        // Parse inputs
        boolean mostRecent = Boolean.parseBoolean(mostRecentString);
        boolean published = Boolean.parseBoolean(publishedString);
        Set<String> tagSet = parseTags(tagsString);

        // Call service
        List<SharedModuleMetadata> metadataList = metadataService.queryAllMetadata(mostRecent, published, where,
                tagSet);
        ResourceList<SharedModuleMetadata> resourceList = new ResourceList<>(metadataList);
        return okResult(resourceList);
    }

    /** Similar to queryAllMetadata, except this only queries on module versions of the specified ID. */
    public Result queryMetadataById(String id, String mostRecentString, String publishedString, String where,
            String tagsString) {
        getAuthenticatedSession(Roles.DEVELOPER);

        // Parse inputs
        boolean mostRecent = Boolean.parseBoolean(mostRecentString);
        boolean published = Boolean.parseBoolean(publishedString);
        Set<String> tagSet = parseTags(tagsString);

        // Call service
        List<SharedModuleMetadata> metadataList = metadataService.queryMetadataById(id, mostRecent, published, where,
                tagSet);
        ResourceList<SharedModuleMetadata> resourceList = new ResourceList<>(metadataList);
        return okResult(resourceList);
    }

    // Helper method to parse tags from URL query params. Package-scoped for unit tests.
    static Set<String> parseTags(String tagsString) {
        // Parse set of tags from a comma-delimited list.
        Set<String> tagSet = new HashSet<>();
        if (StringUtils.isNotBlank(tagsString)) {
            String[] tagsSplit = tagsString.split(",");
            Collections.addAll(tagSet, tagsSplit);
        }
        return tagSet;
    }

    /** Updates metadata for the specified module version. */
    public Result updateMetadata(String id, int version) {
        verifySharedDeveloperAccess();
        SharedModuleMetadata metadata = parseJson(request(), SharedModuleMetadata.class);
        SharedModuleMetadata updatedMetadata = metadataService.updateMetadata(id, version, metadata);
        return okResult(updatedMetadata);
    }

    // Helper method to verify caller permissions for write operations. You need to be a developer in the "shared"
    // study (the study for the Shared Module Library).
    private void verifySharedDeveloperAccess() {
        UserSession session = getAuthenticatedSession(Roles.DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        if (!BridgeConstants.SHARED_STUDY_ID_STRING.equals(studyId.getIdentifier())) {
            throw new UnauthorizedException();
        }
    }
}
