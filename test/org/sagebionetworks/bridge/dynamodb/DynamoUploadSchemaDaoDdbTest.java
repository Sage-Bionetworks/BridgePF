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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.UploadSchemaDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoUploadSchemaDaoDdbTest {
    private static final UploadFieldDefinition FIELD_DEF = new DynamoUploadFieldDefinition.Builder().withName("field")
            .withType(UploadFieldType.STRING).build();
    private static final List<UploadFieldDefinition> FIELD_DEF_LIST = ImmutableList.of(FIELD_DEF);
    private static final int SCHEMA_REV = 7;

    @Autowired
    private UploadSchemaDao dao;

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
        try {
            dao.deleteUploadSchemaById(TestConstants.TEST_STUDY, schemaId);
        } catch (EntityNotFoundException ex) {
            // Schema may have already been deleted.
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
        mapper.save(makeSimpleSchema("Initial Name", SCHEMA_REV, TestConstants.TEST_STUDY_IDENTIFIER, null));

        // Try to create a new schema with the same schema ID
        try {
            mapper.save(makeSimpleSchema("New Schema Conflict", SCHEMA_REV, TestConstants.TEST_STUDY_IDENTIFIER,
                    null));
            fail("expected exception");
        } catch (ConditionalCheckFailedException ex) {
            // expected exception
        }

        // Update from version 1 to version 2
        mapper.save(makeSimpleSchema("Updated Name", SCHEMA_REV, TestConstants.TEST_STUDY_IDENTIFIER, 1L));

        // Try to update version 1 again
        try {
            mapper.save(makeSimpleSchema("Update Schema Conflict", SCHEMA_REV, TestConstants.TEST_STUDY_IDENTIFIER,
                    1L));
            fail("expected exception");
        } catch (ConditionalCheckFailedException ex) {
            // expected exception
        }
    }

    @Test
    public void crudV4() {
        // Create schema w/o specifying rev. This auto-creates rev 1.
        testCreateOneSchema("rev1", null, 1);

        // Create schema with rev 3 (skipping rev 2).
        testCreateOneSchema("rev3", 3, 3);

        // Create schema w/o specifying rev. This should be auto-incremented to rev 4 (not rev 2).
        testCreateOneSchema("rev4", null, 4);

        // Create rev 2 (which is not the highest rev)
        testCreateOneSchema("rev2", 2, 2);

        // Re-create rev 4, get a ConcurrentModificationException.
        UploadSchema recreatingSchema4 = makeSimpleSchema("recreate schema rev 4", 4, null, 1L);
        try {
            dao.createSchemaRevisionV4(TestConstants.TEST_STUDY, recreatingSchema4);
            fail("expected exception");
        } catch (ConcurrentModificationException ex) {
            // expected exception
        }

        // Update schema rev 4. Do a simple update, like changing the name. (Testing field validation is another test.)
        // Again, we don't include the study because the update API is supposed to fill it in. Current version is
        // version 1, so we need to fill that in.
        DynamoUploadSchema updatingSchema4 = (DynamoUploadSchema) makeSimpleSchema("rev4 v2", null, null, 1L);

        // Fill in garbage schema ID and rev, because the update API ignores them and uses the params.
        updatingSchema4.setSchemaId("fake ID");
        updatingSchema4.setRevision(13);

        // Update and validate. Validate that study, schema ID, and rev were set. Version should now be version 2.
        UploadSchema updatedSchema4 = dao.updateSchemaRevisionV4(TestConstants.TEST_STUDY, schemaId, 4,
                updatingSchema4);
        assertSimpleSchema(updatedSchema4, "rev4 v2", 4, TestConstants.TEST_STUDY_IDENTIFIER, 2L);
        UploadSchema fetchedSchema4v2 = dao.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, schemaId, 4);
        assertSimpleSchema(fetchedSchema4v2, "rev4 v2", 4, TestConstants.TEST_STUDY_IDENTIFIER, 2L);

        // Update rev4 v1 again, get a ConcurrentModificationException.
        UploadSchema reupdatingSchema4 = makeSimpleSchema("re-update rev4", null, null, 1L);
        try {
            dao.updateSchemaRevisionV4(TestConstants.TEST_STUDY, schemaId, 4, reupdatingSchema4);
            fail("expected exception");
        } catch (ConcurrentModificationException ex) {
            // expected exception
        }

        // Update rev 5, which doesn't exist. Get an EntityNotFoundException.
        UploadSchema dummySchema = makeSimpleSchema("update rev5", null, null, null);
        try {
            dao.updateSchemaRevisionV4(TestConstants.TEST_STUDY, schemaId, 5, dummySchema);
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            // expected exception
        }
    }

    private void testCreateOneSchema(String name, Integer createdRev, int expectedRev) {
        // Create schema. We include version to simulate creating a new schema by copying an existing one to test that
        // we handle that correctly. We exclude study ID, since clients won't have that, to test that we handle that as
        // well (by getting study ID from parameters).
        UploadSchema schema = makeSimpleSchema(name, createdRev, null, 1L);

        // Created schema has version 1 and has study ID set.
        UploadSchema createdSchema = dao.createSchemaRevisionV4(TestConstants.TEST_STUDY, schema);
        assertSimpleSchema(createdSchema, name, expectedRev, TestConstants.TEST_STUDY_IDENTIFIER, 1L);
        UploadSchema fetchedSchema = dao.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, schemaId, expectedRev);
        assertSimpleSchema(fetchedSchema, name, expectedRev, TestConstants.TEST_STUDY_IDENTIFIER, 1L);
    }

    private UploadSchema makeSimpleSchema(String name, Integer rev, String studyId, Long version) {
        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setFieldDefinitions(FIELD_DEF_LIST);
        schema.setName(name);
        schema.setSchemaId(schemaId);
        schema.setSchemaType(UploadSchemaType.IOS_DATA);
        schema.setStudyId(studyId);
        schema.setVersion(version);

        if (rev != null) {
            schema.setRevision(rev);
        }

        return schema;
    }

    private void assertSimpleSchema(UploadSchema schema, String name, int rev, String studyId, Long version) {
        assertEquals(FIELD_DEF_LIST, schema.getFieldDefinitions());
        assertEquals(name, schema.getName());
        assertEquals(rev, schema.getRevision());
        assertEquals(schemaId, schema.getSchemaId());
        assertEquals(UploadSchemaType.IOS_DATA, schema.getSchemaType());
        assertEquals(studyId, schema.getStudyId());
        assertEquals(version, schema.getVersion());
    }
}
