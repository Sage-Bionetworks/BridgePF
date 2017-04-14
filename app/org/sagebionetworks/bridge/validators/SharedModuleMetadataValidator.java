package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleMetadata;

/** Validator for SharedModuleMetadata. */
public class SharedModuleMetadataValidator implements Validator {
    // package-scoped to facilitate unit tests
    static final int GUID_MAX_LENGTH = 36;
    static final int ID_MAX_LENGTH = 60;
    static final int NAME_MAX_LENGTH = 255;
    static final int NOTES_MAX_LENGTH = 65535;
    static final int OS_MAX_LENGTH = 60;
    static final int SCHEMA_ID_MAX_LENGTH = 60;

    /** Singleton instance of this validator. */
    public static final SharedModuleMetadataValidator INSTANCE = new SharedModuleMetadataValidator();

    /** {@inheritDoc} */
    @Override
    public boolean supports(Class<?> clazz) {
        return SharedModuleMetadata.class.isAssignableFrom(clazz);
    }

    /** {@inheritDoc} */
    @Override
    public void validate(Object target, Errors errors) {
        if (target == null) {
            errors.rejectValue("sharedModuleMetadata", "cannot be null");
        } else if (!(target instanceof SharedModuleMetadata)) {
            errors.rejectValue("sharedModuleMetadata", "is the wrong type");
        } else {
            SharedModuleMetadata metadata = (SharedModuleMetadata) target;

            // id
            String id = metadata.getId();
            if (StringUtils.isBlank(id)) {
                errors.rejectValue("id", "must be specified");
            } else if (metadata.getId().length() > ID_MAX_LENGTH) {
                // ID can't be more than 60 chars because of DB space limitations.
                errors.rejectValue("id", "can't be more than " + ID_MAX_LENGTH + " characters");
            }

            // name
            String name = metadata.getName();
            if (StringUtils.isBlank(name)) {
                errors.rejectValue("name", "must be specified");
            } else if (name.length() > NAME_MAX_LENGTH) {
                errors.rejectValue("name", "can't be more than " + NAME_MAX_LENGTH + " characters");
            }

            // Notes is a TEXT field, which maxes out at 65535.
            String notes = metadata.getNotes();
            if (notes != null && notes.length() > NOTES_MAX_LENGTH) {
                errors.rejectValue("notes", "can't be more than " + NOTES_MAX_LENGTH + " characters");
            }

            // os
            String os = metadata.getOs();
            if (os != null && os.length() > OS_MAX_LENGTH) {
                errors.rejectValue("os", "can't be more than " + OS_MAX_LENGTH + " characters");
            }

            // Set some boolean flags for schema ID and rev and survey guid and createdOn
            String schemaId = metadata.getSchemaId();
            boolean hasSchemaId = schemaId != null;

            Integer schemaRevision = metadata.getSchemaRevision();
            boolean hasSchemaRev = schemaRevision != null;

            Long surveyCreatedOn = metadata.getSurveyCreatedOn();
            boolean hasSurveyCreatedOn = surveyCreatedOn != null;

            String surveyGuid = metadata.getSurveyGuid();
            boolean hasSurveyGuid = surveyGuid != null;

            // schema ID
            if (hasSchemaId) {
                if (StringUtils.isBlank(schemaId)) {
                    errors.rejectValue("schemaId", "can't be empty or blank");
                }
                if (schemaId.length() > SCHEMA_ID_MAX_LENGTH) {
                    errors.rejectValue("schemaId", "can't be more than " + SCHEMA_ID_MAX_LENGTH + " characters");
                }
                if (!hasSchemaRev) {
                    errors.rejectValue("schemaRevision", "must be specified if schemaId is specified");
                }
            }

            // schemaRev
            if (hasSchemaRev) {
                if (schemaRevision <= 0) {
                    errors.rejectValue("schemaRevision", "can't be zero or negative");
                }
                if (!hasSchemaId) {
                    errors.rejectValue("schemaId", "must be specified if schemaRevision is specified");
                }
            }

            // surveyCreatedOn
            if (hasSurveyCreatedOn) {
                if (surveyCreatedOn <= 0) {
                    errors.rejectValue("surveyCreatedOn", "can't be zero or negative");
                }
                if (!hasSurveyGuid) {
                    errors.rejectValue("surveyGuid", "must be specified if surveyCreatedOn is specified");
                }
            }

            // surveyGuid
            if (hasSurveyGuid) {
                if (StringUtils.isBlank(surveyGuid)) {
                    errors.rejectValue("surveyGuid", "can't be empty or blank");
                }
                if (surveyGuid.length() > GUID_MAX_LENGTH) {
                    // Guids are expected to be 36 characters long (with dashes), so we only reserve 36 chars in our DB
                    errors.rejectValue("surveyGuid", "can't be more than " + GUID_MAX_LENGTH + " characters");
                }
                if (!hasSurveyCreatedOn) {
                    errors.rejectValue("surveyCreatedOn", "must be specified if surveyGuid is specified");
                }
            }

            // can't have neither schema nor survey
            if (!hasSchemaId && !hasSurveyGuid) {
                errors.rejectValue("sharedModuleMetadata", "must contain either schemaId or surveyGuid");
            }

            // can't have both shcema and survey
            if (hasSchemaId && hasSurveyGuid) {
                errors.rejectValue("sharedModuleMetadata", "can't contain both schemaId and surveyGuid");
            }

            // version
            if (metadata.getVersion() <= 0) {
                errors.rejectValue("version", "can't be zero or negative");
            }
        }
    }
}
