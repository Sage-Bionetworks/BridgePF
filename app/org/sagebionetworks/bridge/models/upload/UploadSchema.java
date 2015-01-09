package org.sagebionetworks.bridge.models.upload;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Strings;
import org.springframework.validation.Errors;

import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.validators.Validate;

/**
 * This class represents a schema for the uploads sent by the mobile apps. This can be created and updated by study
 * researchers.
 */
@BridgeTypeName("UploadSchema")
@JsonDeserialize(as = DynamoUploadSchema.class)
public interface UploadSchema extends BridgeEntity {
    /** A list of fields defined in the schema. This can be changed across different schema revisions. */
    List<UploadFieldDefinition> getFieldDefinitions();

    /**
     * Human-friendly displayable schema name, such as "Tapping Activity Task". This can be changed across different
     * schema revisions.
     */
    String getName();

    /**
     * Schema revision number. This is managed by the Bridge back-end. For creating new schemas, this should initially
     * be unset (or set to the default value of zero). For updating schemas, this should be set to the revision number
     * of the schema you are updating, to ensure that you aren't updating an older version of the schema. Upon creating
     * or updating a schema, the Bridge back-end will automatically increment this revision number by 1 (for updating
     * existing schemas) or from 0 to 1 (for creating new schemas).
     */
    int getRevision();

    /**
     * Unique identifier for the schema. This need only be unique to a given study. This should included in the upload
     * data. This can be human readable, such as "tapping-task". This cannot be changed across different schema
     * revisions.
     */
    String getSchemaId();

    /** Study ID for the study this schema lives under. This cannot be changed across different schema revisions. */
    String getStudyId();

    /** This class validates the intrinsic properties of the schema. */
    public static class Validator implements org.springframework.validation.Validator {
        /** Singleton instance of this validator. */
        public static final Validator INSTANCE = new Validator();

        /** {@inheritDoc} */
        @Override
        public boolean supports(Class<?> clazz) {
            return UploadSchema.class.isAssignableFrom(clazz);
        }

        /**
         * <p>
         * Validates the given object as a valid UploadSchema instance. This will flag errors in the following
         * conditions:
         *   <ul>
         *     <li>value is null or not an UploadSchema</li>
         *     <li>fieldDefinitions is null or empty</li>
         *     <li>fieldDefinitions contains null or invalid entries</li>
         *     <li>name is null or empty</li>
         *     <li>revision is negative</li>
         *     <li>schemaId is null or empty</li>
         *     <li>studyId is null or empty</li>
         *   </ul>
         * </p>
\         *
         * @see org.springframework.validation.Validator#validate
         */
        @Override
        public void validate(Object target, Errors errors) {
            if (target == null) {
                errors.rejectValue("uploadSchema", Validate.CANNOT_BE_NULL);
            } else if (!(target instanceof UploadSchema)) {
                errors.rejectValue("uploadSchema", Validate.WRONG_TYPE);
            } else {
                UploadSchema uploadSchema = (UploadSchema) target;

                // fieldDefinitions
                // We do not need to validate inside the elements of fieldDefinitions, because (1)
                // UploadFieldDefinition is self-validating and (2) we copy this to an ImmutableList, which does not
                // permit null values.
                List<UploadFieldDefinition> fieldDefList = uploadSchema.getFieldDefinitions();
                if (fieldDefList == null || fieldDefList.isEmpty()) {
                    errors.rejectValue("fieldDefinitions", Validate.CANNOT_BE_NULL_OR_EMPTY);
                }

                // name
                if (Strings.isNullOrEmpty(uploadSchema.getName())) {
                    errors.rejectValue("name", Validate.CANNOT_BE_BLANK);
                }

                // revision must be non-negative. (0 is allowed if it's a new schema. revisions 1 and above are saved
                // schemas)
                if (uploadSchema.getRevision() < 0) {
                    errors.rejectValue("revision", Validate.CANNOT_BE_NEGATIVE);
                }

                // schema ID
                if (Strings.isNullOrEmpty(uploadSchema.getSchemaId())) {
                    errors.rejectValue("schemaId", Validate.CANNOT_BE_BLANK);
                }

                // study ID
                if (Strings.isNullOrEmpty(uploadSchema.getStudyId())) {
                    errors.rejectValue("studyId", Validate.CANNOT_BE_BLANK);
                }
            }
        }
    }
}
