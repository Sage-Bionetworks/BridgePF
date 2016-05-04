package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoUploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;

public class StrictValidationHandlerComputeIsRequiredTest {
    private static final Object[][] TEST_CASES = {
            { null, false, null, null, false, "required false" },
            { null, true, null, null, true, "null app version" },
            { 15, true, null, null, true, "no min or max app version" },
            { 5, true, 10, null, false, "below min app version" },
            { 15, true, 10, null, true, "above min, no max" },
            { 15, true, null, 20, true, "no min, below max" },
            { 25, true, null, 20, false, "above max app version" },
            { 5, true, 10, 20, false, "below min and max" },
            { 15, true, 10, 20, true, "between min and max" },
            { 25, true, 10, 20, false, "above min and max" },
    };

    private static void test(Integer appVersion, boolean fieldIsRequired, Integer minAppVersion, Integer maxAppVersion,
            boolean expected, String message) {
        UploadFieldDefinition fieldDef = new DynamoUploadFieldDefinition.Builder().withName("dummy")
                .withType(UploadFieldType.STRING).withRequired(fieldIsRequired).withMinAppVersion(minAppVersion)
                .withMaxAppVersion(maxAppVersion).build();
        boolean result = StrictValidationHandler.computeIsRequired(appVersion, fieldDef);
        assertEquals(message, expected, result);
    }

    @Test
    public void test() {
        for (Object[] oneTestCase : TEST_CASES) {
            test((Integer) oneTestCase[0], (boolean) oneTestCase[1], (Integer) oneTestCase[2],
                    (Integer) oneTestCase[3], (boolean) oneTestCase[4], (String) oneTestCase[5]);
        }
    }
}
