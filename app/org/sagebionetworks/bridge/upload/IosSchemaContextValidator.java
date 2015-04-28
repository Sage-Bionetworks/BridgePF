package org.sagebionetworks.bridge.upload;

import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;

import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;

/** Validates the upload validation context between the new and old IosSchemaValidationHandler. */
public class IosSchemaContextValidator implements ContextValidator {
    private static final Joiner ERROR_MESSAGE_LIST_JOINER = Joiner.on(", ");

    public static final IosSchemaContextValidator INSTANCE = new IosSchemaContextValidator();

    /** {@inheritDoc} */
    @Override
    public void validate(@Nonnull UploadValidationContext productionContext,
            @Nonnull UploadValidationContext testContext) throws UploadValidationException {
        // First, check if it's a survey. Surveys are handled differently in v2.
        HealthDataRecordBuilder prodRecordBuilder = productionContext.getHealthDataRecordBuilder();
        if (prodRecordBuilder == null) {
            throw new UploadValidationException("Production context has no record builder");
        }
        String prodSchemaId = prodRecordBuilder.getSchemaId();
        if (StringUtils.isBlank(prodSchemaId)) {
            throw new UploadValidationException("Production context has no schema");
        }

        if (prodSchemaId.equals(IosSchemaValidationHandler.SCHEMA_IOS_SURVEY)) {
            // Surveys are handled differently in v2, so we don't need to validate surveys.
            return;
        }

        // IosSchemaValidationHandler writes to the record builder and the attachment map. In the record builder, the
        // most important attributes are the schema (id and rev) and the data.

        // Validate record builders exist. Prod record builder and schema ID have already been null checked.
        HealthDataRecordBuilder testRecordBuilder = testContext.getHealthDataRecordBuilder();
        if (testRecordBuilder == null) {
            throw new UploadValidationException("Test context has no record builder");
        }

        // validate schema ID
        String testSchemaId = testRecordBuilder.getSchemaId();
        if (StringUtils.isBlank(testSchemaId)) {
            throw new UploadValidationException("Test context has no schema");
        }
        if (!prodSchemaId.equals(testSchemaId)) {
            throw new UploadValidationException(String.format("Schema mismatch, prod has %s, test as %s", prodSchemaId,
                    testSchemaId));
        }

        // validate schema rev
        int prodSchemaRev = prodRecordBuilder.getSchemaRevision();
        int testSchemaRev = testRecordBuilder.getSchemaRevision();
        if (prodSchemaRev != testSchemaRev) {
            throw new UploadValidationException(String.format("Schema revision mismatch, prod has %d, test has %d",
                    prodSchemaRev, testSchemaRev));
        }

        // validate data map exists
        JsonNode prodJsonData = prodRecordBuilder.getData();
        if (prodJsonData == null || prodJsonData.isNull()) {
            throw new UploadValidationException("Prod record builder has no data");
        }
        JsonNode testJsonData = testRecordBuilder.getData();
        if (testJsonData == null || testJsonData.isNull()) {
            throw new UploadValidationException("Test record builder has no data");
        }

        // get data map keys and compare the keys
        Set<String> prodJsonDataKeySet = ImmutableSet.copyOf(prodJsonData.fieldNames());
        Set<String> testJsonDataKeySet = ImmutableSet.copyOf(testJsonData.fieldNames());
        validateKeys(prodJsonDataKeySet, testJsonDataKeySet, "record data fields");

        // validate attachment map exists
        Map<String, byte[]> prodAttachmentMap = productionContext.getAttachmentsByFieldName();
        if (prodAttachmentMap == null) {
            throw new UploadValidationException("Prod context has no attachment map");
        }
        Map<String, byte[]> testAttachmentMap = testContext.getAttachmentsByFieldName();
        if (testAttachmentMap == null) {
            throw new UploadValidationException("Test context has no attachment map");
        }

        // get attachment map keys and compare the keys
        Set<String> prodAttachmentKeySet = prodAttachmentMap.keySet();
        Set<String> testAttachmentKeySet = testAttachmentMap.keySet();
        validateKeys(prodAttachmentKeySet, testAttachmentKeySet, "attachment map fields");
    }

    /**
     * Compares the key sets between prod and test. For example, these could be field names in the JSON map or in the
     * attachment map. The key name is used to construct the error message. This should be something like "record data
     * fields" or "attachment map fields".
     */
    private static void validateKeys(Set<String> prodKeySet, Set<String> testKeySet, String keyName)
            throws UploadValidationException {
        if (!prodKeySet.equals(testKeySet)) {
            Sets.SetView<String> inProdButNotTest = Sets.difference(prodKeySet, testKeySet);
            if (!inProdButNotTest.isEmpty()) {
                throw new UploadValidationException(String.format("Test has missing %s: %s", keyName,
                        ERROR_MESSAGE_LIST_JOINER.join(inProdButNotTest)));
            }

            Sets.SetView<String> inTestButNotProd = Sets.difference(testKeySet, prodKeySet);
            if (!inTestButNotProd.isEmpty()) {
                throw new UploadValidationException(String.format("Test extraneous record %s: %s", keyName,
                        ERROR_MESSAGE_LIST_JOINER.join(inTestButNotProd)));
            }
        }
    }
}
