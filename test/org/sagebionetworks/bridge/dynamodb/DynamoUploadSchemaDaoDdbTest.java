package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.List;
import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoUploadSchemaDaoDdbTest {
    private static final int SCHEMA_REV = 7;

    @Resource(name = "uploadSchemaDdbMapper")
    private DynamoDBMapper mapper;

    private String schemaId;

    @Before
    public void setup() {
        // This runs before each method, creates a new schema ID for each method.
        schemaId = TestUtils.randomName(DynamoUploadSchemaDaoDdbTest.class);
    }

    @After
    public void cleanup() {
        DynamoUploadSchema key = new DynamoUploadSchema();
        key.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        key.setSchemaId(schemaId);
        key.setRevision(SCHEMA_REV);
        DynamoUploadSchema testSchema = mapper.load(key);

        if (testSchema != null) {
            mapper.delete(testSchema);
        }
    }

    @Test
    public void ddbRoundtrip() {
        // create test schema
        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setName("Test Schema");
        schema.setRevision(SCHEMA_REV);
        schema.setSchemaId(schemaId);
        schema.setSchemaType(UploadSchemaType.IOS_SURVEY);
        schema.setSurveyGuid("test-survey-guid");
        schema.setSurveyCreatedOn(1337L);
        schema.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);

        UploadFieldDefinition fieldDef = new DynamoUploadFieldDefinition.Builder().withName("test-field")
                .withType(UploadFieldType.STRING).build();
        schema.setFieldDefinitions(ImmutableList.of(fieldDef));

        // Save it to DDB, then read it back and make sure it has the same fields. (Can't use .equals(), even if we
        // implemented it, because the mapper functions modify the object we pass in.)
        mapper.save(schema);
        DynamoUploadSchema savedSchema = mapper.load(schema);

        // validate fields
        assertEquals("Test Schema", savedSchema.getName());
        assertEquals(SCHEMA_REV, savedSchema.getRevision());
        assertEquals(schemaId, savedSchema.getSchemaId());
        assertEquals(UploadSchemaType.IOS_SURVEY, savedSchema.getSchemaType());
        assertEquals("test-survey-guid", savedSchema.getSurveyGuid());
        assertEquals(1337, savedSchema.getSurveyCreatedOn().longValue());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, savedSchema.getStudyId());

        List<UploadFieldDefinition> returnedFieldDefList = savedSchema.getFieldDefinitions();
        assertEquals(1, returnedFieldDefList.size());

        UploadFieldDefinition returnedFieldDef = returnedFieldDefList.get(0);
        assertEquals("test-field", returnedFieldDef.getName());
        assertEquals(UploadFieldType.STRING, returnedFieldDef.getType());
    }

    @Test
    public void concurrentModification() {
        // just the keys: study ID, schema ID, and rev
        mapper.save(makeSimpleSchema("Initial Name", schemaId, null));

        // Try to create a new schema with the same schema ID
        try {
            mapper.save(makeSimpleSchema("New Schema Conflict", schemaId, null));
            fail("expected exception");
        } catch (ConditionalCheckFailedException ex) {
            // expected exception
        }

        // Update from version 1 to version 2
        mapper.save(makeSimpleSchema("Updated Name", schemaId, 1L));

        // Try to update version 1 again
        try {
            mapper.save(makeSimpleSchema("Update Schema Conflict", schemaId, 1L));
            fail("expected exception");
        } catch (ConditionalCheckFailedException ex) {
            // expected exception
        }
    }

    private static DynamoUploadSchema makeSimpleSchema(String name, String schemaId, Long version) {
        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setName(name);
        schema.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        schema.setSchemaId(schemaId);
        schema.setRevision(SCHEMA_REV);
        schema.setVersion(version);
        return schema;
    }
}
