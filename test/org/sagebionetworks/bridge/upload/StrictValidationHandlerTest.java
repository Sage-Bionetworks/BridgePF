package org.sagebionetworks.bridge.upload;

import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadFieldDefinition;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.services.UploadSchemaService;

public class StrictValidationHandlerTest {
    private final static byte[] DUMMY_ATTACHMENT = new byte[0];

    private UploadValidationContext context;
    private StrictValidationHandler handler;

    @Before
    public void setup() {
        // Mock out shouldThrow to always return true. This forces strict validation in tests, which is what we want.
        // TODO: replace this when we implement per-study config lookup
        handler = spy(new StrictValidationHandler());
        doReturn(true).when(handler).shouldThrow(notNull(StudyIdentifier.class));

        // Set up common context attributes.
        context.setStudy(TestConstants.TEST_STUDY);

        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId("test-upload");
        context.setUpload(upload);
    }

    // happy case
    //   missing/present optional attachment
    //   missing/present optional JSON field
    @Test
    public void happyCase() {
        // Schema for test. All that matters is field def list.
        // Test one of each type.
        DynamoUploadSchema testSchema = new DynamoUploadSchema();
        testSchema.setFieldDefinitions(ImmutableList.<UploadFieldDefinition>of(
                new DynamoUploadFieldDefinition.Builder().withName("attachment blob")
                        .withType(UploadFieldType.ATTACHMENT_BLOB).build(),
                new DynamoUploadFieldDefinition.Builder().withName("attachment csv")
                        .withType(UploadFieldType.ATTACHMENT_CSV).build(),
                new DynamoUploadFieldDefinition.Builder().withName("attachment json blob")
                        .withType(UploadFieldType.ATTACHMENT_JSON_BLOB).build(),
                new DynamoUploadFieldDefinition.Builder().withName("attachment json table")
                        .withType(UploadFieldType.ATTACHMENT_JSON_TABLE).build(),
                new DynamoUploadFieldDefinition.Builder().withName("boolean")
                        .withType(UploadFieldType.BOOLEAN).build(),
                new DynamoUploadFieldDefinition.Builder().withName("calendar date")
                        .withType(UploadFieldType.CALENDAR_DATE).build(),
                new DynamoUploadFieldDefinition.Builder().withName("float")
                        .withType(UploadFieldType.FLOAT).build(),
                new DynamoUploadFieldDefinition.Builder().withName("float with int value")
                        .withType(UploadFieldType.FLOAT).build(),
                new DynamoUploadFieldDefinition.Builder().withName("inline json blob")
                        .withType(UploadFieldType.INLINE_JSON_BLOB).build(),
                new DynamoUploadFieldDefinition.Builder().withName("int")
                        .withType(UploadFieldType.INT).build(),
                new DynamoUploadFieldDefinition.Builder().withName("int with float value")
                        .withType(UploadFieldType.INT).build(),
                new DynamoUploadFieldDefinition.Builder().withName("string")
                        .withType(UploadFieldType.STRING).build(),
                new DynamoUploadFieldDefinition.Builder().withName("string timestamp")
                        .withType(UploadFieldType.TIMESTAMP).build(),
                new DynamoUploadFieldDefinition.Builder().withName("long timestamp")
                        .withType(UploadFieldType.TIMESTAMP).build(),
                new DynamoUploadFieldDefinition.Builder().withName("missing optional attachment")
                        .withType(UploadFieldType.ATTACHMENT_BLOB).withRequired(false).build(),
                new DynamoUploadFieldDefinition.Builder().withName("present optional attachment")
                        .withType(UploadFieldType.ATTACHMENT_BLOB).withRequired(false).build(),
                new DynamoUploadFieldDefinition.Builder().withName("missing optional json")
                        .withType(UploadFieldType.STRING).withRequired(false).build(),
                new DynamoUploadFieldDefinition.Builder().withName("present optional json")
                        .withType(UploadFieldType.STRING).withRequired(false).build()));

        // mock schema service
        UploadSchemaService mockSchemaService = mock(UploadSchemaService.class);
        when(mockSchemaService.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, "test-study", 1)).thenReturn(
                testSchema);

        // set up attachments map
        Map<String, byte[]> attachmentsMap = ImmutableMap.of(
                "attachment blob", DUMMY_ATTACHMENT,
                "attachment csv", DUMMY_ATTACHMENT,
                "attachment json blob", DUMMY_ATTACHMENT,
                "attachment json table", DUMMY_ATTACHMENT,
                "present optional attachment", DUMMY_ATTACHMENT);

        // set up JSON data
        String jsonString = "{\n" +
                "   \"boolean\":true,\n" +
                "   \"calendar date\":\"2015-07-24\",\n" +
                "   \"float\":3.14,\n" +
                "   \"float with int value\":13,\n" +
                "   \"inline json blob\":[\"inline\", \"json\", \"blob\"],\n" +
                "   \"int\":42,\n" +
                "   \"int with float value\":2.78,\n" +
                "   \"string\":\"This is a string\",\n" +
                "   \"string timestamp\":\"2015-07-24T18:49:54-07:00\",\n" +
                "   \"long timestamp\":1437787098066,\n" +
                "   \"present optional json\":\"optional, but present\"\n" +
                "}";
    }

    // missing required attachment
    // missing required field
    // optional field still gets validated
    // JSON null required field
    // invalid boolean
    // non-string calendar date
    // malformatted calendar date
    // invalid float
    // invalid int
    // invalid string
    // malformatted timestamp
    // wrong type timestamp
    // multiple validation errors
}
