package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.UploadSchemaDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;

public class UploadSchemaServiceTest {
    private static final UploadFieldDefinition FIELD_DEF = new UploadFieldDefinition.Builder().withName("field")
            .withType(UploadFieldType.STRING).build();
    private static final List<UploadFieldDefinition> FIELD_DEF_LIST = ImmutableList.of(FIELD_DEF);
    private static final String OS_NAME = "unit-test-os";
    private static final String SCHEMA_ID = "test-schema";
    private static final String SCHEMA_NAME = "My Schema";
    private static final int SCHEMA_REV = 1;

    private UploadSchema svcInputSchema;
    private UploadSchemaDao dao;
    private UploadSchemaService svc;

    @Before
    public void setup() {
        svcInputSchema = makeSimpleSchema();
        dao = mock(UploadSchemaDao.class);
        svc = new UploadSchemaService();
        svc.setUploadSchemaDao(dao);
    }

    @Test(expected = BadRequestException.class)
    public void createV4NullSchemaId() {
        svcInputSchema.setSchemaId(null);
        svc.createSchemaRevisionV4(TestConstants.TEST_STUDY, svcInputSchema);
    }

    @Test(expected = BadRequestException.class)
    public void createV4EmptySchemaId() {
        svcInputSchema.setSchemaId("");
        svc.createSchemaRevisionV4(TestConstants.TEST_STUDY, svcInputSchema);
    }

    @Test(expected = BadRequestException.class)
    public void createV4BlankSchemaId() {
        svcInputSchema.setSchemaId("   ");
        svc.createSchemaRevisionV4(TestConstants.TEST_STUDY, svcInputSchema);
    }

    @Test
    public void createV4InvalidSchema() {
        try {
            svc.createSchemaRevisionV4(TestConstants.TEST_STUDY, makeInvalidSchema());
            fail("expected exception");
        } catch (InvalidEntityException ex) {
            assertSchemaValidationException(ex);
        }
    }

    @Test
    public void createV4() {
        // no old schema, create with default rev
        createV4TestHelper(1, 0, null);

        // no old schema, create with rev 3
        createV4TestHelper(3, 3, null);

        // old schema rev 1, create with default rev
        createV4TestHelper(2, 0, 1);

        // old schema rev 1, create with rev 3
        createV4TestHelper(3, 3, 1);
    }

    private void createV4TestHelper(int expectedRev, int inputRev, Integer oldRev) {
        svcInputSchema.setRevision(inputRev);

        // mock dao
        ArgumentCaptor<UploadSchema> daoInputSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        UploadSchema daoOutputSchema = makeSimpleSchema();
        when(dao.createSchemaRevision(daoInputSchemaCaptor.capture())).thenReturn(daoOutputSchema);

        if (oldRev != null) {
            // get should return a schema
            UploadSchema oldSchema = makeSimpleSchema();
            oldSchema.setRevision(oldRev);
            when(dao.getUploadSchemaLatestRevisionById(TestConstants.TEST_STUDY, SCHEMA_ID)).thenReturn(oldSchema);
        }

        // execute
        UploadSchema svcOutputSchema = svc.createSchemaRevisionV4(TestConstants.TEST_STUDY, svcInputSchema);

        // Validate we set key parameters when passing the schema to the DAO, including study ID and rev.
        UploadSchema daoInputSchema = daoInputSchemaCaptor.getValue();
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, daoInputSchema.getStudyId());
        assertEquals(expectedRev, daoInputSchema.getRevision());

        // Validate DAO input is also svcOutput.
        assertSame(daoOutputSchema, svcOutputSchema);
    }

    @Test(expected = BadRequestException.class)
    public void createOrUpdateNullSchemaId() {
        svcInputSchema.setSchemaId(null);
        svc.createOrUpdateUploadSchema(TestConstants.TEST_STUDY, svcInputSchema);
    }

    @Test(expected = BadRequestException.class)
    public void createOrUpdateEmptySchemaId() {
        svcInputSchema.setSchemaId("");
        svc.createOrUpdateUploadSchema(TestConstants.TEST_STUDY, svcInputSchema);
    }

    @Test(expected = BadRequestException.class)
    public void createOrUpdateBlankSchemaId() {
        svcInputSchema.setSchemaId("   ");
        svc.createOrUpdateUploadSchema(TestConstants.TEST_STUDY, svcInputSchema);
    }

    @Test
    public void createOrUpdateInvalidSchema() {
        try {
            svc.createOrUpdateUploadSchema(TestConstants.TEST_STUDY, makeInvalidSchema());
            fail("expected exception");
        } catch (InvalidEntityException ex) {
            assertSchemaValidationException(ex);
        }
    }

    @Test
    public void createOrUpdate() {
        // no old schema, create with rev 0
        createOrUpdateTestHelper(1, false, 0, null);

        // no old schema, create with rev 3 (throws)
        createOrUpdateTestHelper(null, true, 3, null);

        // old schema rev 1, create with rev 0 (throws)
        createOrUpdateTestHelper(null, true, 0, 1);

        // old schema rev 1, create with rev 1
        createOrUpdateTestHelper(2, false, 1, 1);

        // old schema rev 1, create with rev 2 (throws)
        createOrUpdateTestHelper(null, true, 2, 1);

        // old schema rev 1, create with rev 3 (throws)
        createOrUpdateTestHelper(null, true, 3, 1);
    }

    private void createOrUpdateTestHelper(Integer expectedRev, boolean expectedThrow, int inputRev, Integer oldRev) {
        svcInputSchema.setRevision(inputRev);

        // mock dao
        ArgumentCaptor<UploadSchema> daoInputSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        UploadSchema daoOutputSchema = makeSimpleSchema();
        when(dao.createSchemaRevision(daoInputSchemaCaptor.capture())).thenReturn(daoOutputSchema);

        if (oldRev != null) {
            // get should return a schema
            UploadSchema oldSchema = makeSimpleSchema();
            oldSchema.setRevision(oldRev);
            when(dao.getUploadSchemaLatestRevisionById(TestConstants.TEST_STUDY, SCHEMA_ID)).thenReturn(oldSchema);
        }

        // execute
        if (expectedThrow) {
            try {
                svc.createOrUpdateUploadSchema(TestConstants.TEST_STUDY, svcInputSchema);
                fail("expected exception");
            } catch (ConcurrentModificationException ex) {
                // expected exception
            }
        } else {
            UploadSchema svcOutputSchema = svc.createOrUpdateUploadSchema(TestConstants.TEST_STUDY, svcInputSchema);

            // Validate we set key parameters when passing the schema to the DAO, including study ID and rev.
            UploadSchema daoInputSchema = daoInputSchemaCaptor.getValue();
            assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, daoInputSchema.getStudyId());
            assertEquals(expectedRev.intValue(), daoInputSchema.getRevision());

            // Validate DAO input is also svcOutput.
            assertSame(daoOutputSchema, svcOutputSchema);
        }
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdNullId() {
        svc.deleteUploadSchemaById(TestConstants.TEST_STUDY, null);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdEmptyId() {
        svc.deleteUploadSchemaById(TestConstants.TEST_STUDY, "");
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdBlankId() {
        svc.deleteUploadSchemaById(TestConstants.TEST_STUDY, "   ");
    }

    @Test(expected = EntityNotFoundException.class)
    public void deleteByIdNotFound() {
        // mock dao to return empty list instead of null
        when(dao.getUploadSchemaAllRevisionsById(TestConstants.TEST_STUDY, SCHEMA_ID)).thenReturn(ImmutableList.of());

        svc.deleteUploadSchemaById(TestConstants.TEST_STUDY, SCHEMA_ID);
    }

    @Test
    public void deleteByIdSuccess() {
        // mock dao
        List<UploadSchema> schemaListToDelete = ImmutableList.of(makeSimpleSchema());
        when(dao.getUploadSchemaAllRevisionsById(TestConstants.TEST_STUDY, SCHEMA_ID)).thenReturn(schemaListToDelete);

        // execute and verify delete call
        svc.deleteUploadSchemaById(TestConstants.TEST_STUDY, SCHEMA_ID);
        verify(dao).deleteUploadSchemas(schemaListToDelete);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAndRevNullId() {
        svc.deleteUploadSchemaByIdAndRevision(TestConstants.TEST_STUDY, null, SCHEMA_REV);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAndRevEmptyId() {
        svc.deleteUploadSchemaByIdAndRevision(TestConstants.TEST_STUDY, "", SCHEMA_REV);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAndRevBlankId() {
        svc.deleteUploadSchemaByIdAndRevision(TestConstants.TEST_STUDY, "   ", SCHEMA_REV);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAndRevNegativeRev() {
        svc.deleteUploadSchemaByIdAndRevision(TestConstants.TEST_STUDY, SCHEMA_ID, -1);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAndRevZeroRev() {
        svc.deleteUploadSchemaByIdAndRevision(TestConstants.TEST_STUDY, SCHEMA_ID, 0);
    }

    @Test(expected = EntityNotFoundException.class)
    public void deleteByIdAndRevNotFound() {
        // mock dao to return null
        when(dao.getUploadSchemaByIdAndRevision(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV)).thenReturn(null);

        svc.deleteUploadSchemaByIdAndRevision(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV);
    }

    @Test
    public void deleteByIdAndRevSuccess() {
        // mock dao
        UploadSchema schemaToDelete = makeSimpleSchema();
        when(dao.getUploadSchemaByIdAndRevision(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV)).thenReturn(
                schemaToDelete);

        // execute and verify delete call
        svc.deleteUploadSchemaByIdAndRevision(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV);
        verify(dao).deleteUploadSchemas(ImmutableList.of(schemaToDelete));
    }

    @Test
    public void allSchemasAllRevisions() {
        // mock dao
        List<UploadSchema> daoOutputSchemaList = ImmutableList.of(makeSimpleSchema());
        when(dao.getAllUploadSchemasAllRevisions(TestConstants.TEST_STUDY)).thenReturn(daoOutputSchemaList);

        // execute and validate
        List<UploadSchema> svcOutputSchemaList = svc.getAllUploadSchemasAllRevisions(TestConstants.TEST_STUDY);
        assertSame(daoOutputSchemaList, svcOutputSchemaList);
    }

    @Test
    public void allSchemasLatestRevisions() {
        // Set up dao output. Let's have 2 schemas with 2 revisions each.
        UploadSchema schemaARev1 = makeSimpleSchema();
        schemaARev1.setSchemaId("schema-A");
        schemaARev1.setRevision(1);

        UploadSchema schemaARev2 = makeSimpleSchema();
        schemaARev2.setSchemaId("schema-A");
        schemaARev2.setRevision(2);

        UploadSchema schemaBRev3 = makeSimpleSchema();
        schemaBRev3.setSchemaId("schema-B");
        schemaBRev3.setRevision(3);

        UploadSchema schemaBRev4 = makeSimpleSchema();
        schemaBRev4.setSchemaId("schema-B");
        schemaBRev4.setRevision(4);

        List<UploadSchema> daoOutputSchemaList = ImmutableList.of(schemaARev1, schemaARev2, schemaBRev3, schemaBRev4);
        when(dao.getAllUploadSchemasAllRevisions(TestConstants.TEST_STUDY)).thenReturn(daoOutputSchemaList);

        // execute and validate
        List<UploadSchema> svcOutputSchemaList = svc.getUploadSchemasForStudy(TestConstants.TEST_STUDY);
        assertEquals(2, svcOutputSchemaList.size());

        // List might be in any order, so convert it to a map.
        Map<String, UploadSchema> svcOutputSchemasById = Maps.uniqueIndex(svcOutputSchemaList,
                UploadSchema::getSchemaId);
        assertEquals(2, svcOutputSchemasById.size());
        assertEquals(2, svcOutputSchemasById.get("schema-A").getRevision());
        assertEquals(4, svcOutputSchemasById.get("schema-B").getRevision());
    }

    @Test(expected = BadRequestException.class)
    public void getUploadSchemaNullId() {
        svc.getUploadSchema(TestConstants.TEST_STUDY, null);
    }

    @Test(expected = BadRequestException.class)
    public void getUploadSchemaEmptyId() {
        svc.getUploadSchema(TestConstants.TEST_STUDY, "");
    }

    @Test(expected = BadRequestException.class)
    public void getUploadSchemaBlankId() {
        svc.getUploadSchema(TestConstants.TEST_STUDY, "   ");
    }

    @Test(expected = EntityNotFoundException.class)
    public void getUploadSchemaNotFound() {
        // mock dao to return null
        when(dao.getUploadSchemaLatestRevisionById(TestConstants.TEST_STUDY, SCHEMA_ID)).thenReturn(null);

        svc.getUploadSchema(TestConstants.TEST_STUDY, SCHEMA_ID);
    }

    @Test
    public void getUploadSchemaSuccess() {
        // mock dao
        UploadSchema daoOutputSchema = makeSimpleSchema();
        when(dao.getUploadSchemaLatestRevisionById(TestConstants.TEST_STUDY, SCHEMA_ID)).thenReturn(daoOutputSchema);

        // execute and validate
        UploadSchema svcOutputSchema = svc.getUploadSchema(TestConstants.TEST_STUDY, SCHEMA_ID);
        assertSame(daoOutputSchema, svcOutputSchema);
    }

    @Test(expected = BadRequestException.class)
    public void getSchemaAllRevisionsNullId() {
        svc.getUploadSchemaAllRevisions(TestConstants.TEST_STUDY, null);
    }

    @Test(expected = BadRequestException.class)
    public void getSchemaAllRevisionsEmptyId() {
        svc.getUploadSchemaAllRevisions(TestConstants.TEST_STUDY, "");
    }

    @Test(expected = BadRequestException.class)
    public void getSchemaAllRevisionsBlankId() {
        svc.getUploadSchemaAllRevisions(TestConstants.TEST_STUDY, "   ");
    }

    @Test(expected = EntityNotFoundException.class)
    public void getSchemaAllRevisionsNotFound() {
        // mock dao to return empty list
        when(dao.getUploadSchemaAllRevisionsById(TestConstants.TEST_STUDY, SCHEMA_ID)).thenReturn(ImmutableList.of());

        svc.getUploadSchemaAllRevisions(TestConstants.TEST_STUDY, SCHEMA_ID);
    }

    @Test
    public void getSchemaAllRevisionsSuccess() {
        // mock dao
        List<UploadSchema> daoOutputSchemaList = ImmutableList.of(makeSimpleSchema());
        when(dao.getUploadSchemaAllRevisionsById(TestConstants.TEST_STUDY, SCHEMA_ID)).thenReturn(daoOutputSchemaList);

        // execute and validate
        List<UploadSchema> svcOutputSchemaList = svc.getUploadSchemaAllRevisions(TestConstants.TEST_STUDY, SCHEMA_ID);
        assertSame(daoOutputSchemaList, svcOutputSchemaList);
    }

    @Test(expected = BadRequestException.class)
    public void getByIdAndRevNullId() {
        svc.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, null, SCHEMA_REV);
    }

    @Test(expected = BadRequestException.class)
    public void getByIdAndRevEmptyId() {
        svc.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, "", SCHEMA_REV);
    }

    @Test(expected = BadRequestException.class)
    public void getByIdAndRevBlankId() {
        svc.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, "   ", SCHEMA_REV);
    }

    @Test(expected = BadRequestException.class)
    public void getByIdAndRevNegativeRev() {
        svc.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, SCHEMA_ID, -1);
    }

    @Test(expected = BadRequestException.class)
    public void getByIdAndRevZeroRev() {
        svc.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, SCHEMA_ID, 0);
    }

    @Test(expected = EntityNotFoundException.class)
    public void getByIdAndRevNotFound() {
        // mock dao to return null
        when(dao.getUploadSchemaByIdAndRevision(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV)).thenReturn(null);

        svc.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV);
    }

    @Test
    public void getByIdAndRevSuccess() {
        // mock dao
        UploadSchema daoOutputSchema = makeSimpleSchema();
        when(dao.getUploadSchemaByIdAndRevision(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV)).thenReturn(
                daoOutputSchema);

        // execute and validate
        UploadSchema svcOutputSchema = svc.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV);
        assertSame(daoOutputSchema, svcOutputSchema);
    }

    @Test(expected = BadRequestException.class)
    public void getLatestNullId() {
        svc.getLatestUploadSchemaRevisionForAppVersion(TestConstants.TEST_STUDY, null, ClientInfo.UNKNOWN_CLIENT);
    }

    @Test(expected = BadRequestException.class)
    public void getLatestEmptyId() {
        svc.getLatestUploadSchemaRevisionForAppVersion(TestConstants.TEST_STUDY, "", ClientInfo.UNKNOWN_CLIENT);
    }

    @Test(expected = BadRequestException.class)
    public void getLatestBlankId() {
        svc.getLatestUploadSchemaRevisionForAppVersion(TestConstants.TEST_STUDY, "   ", ClientInfo.UNKNOWN_CLIENT);
    }

    @Test
    public void getLatestMatchMultiple() {
        setupDaoForGetLatest();

        // make client info
        ClientInfo clientInfo = new ClientInfo.Builder().withOsName(OS_NAME).withAppVersion(25).build();

        // execute and validate
        UploadSchema retval = svc.getLatestUploadSchemaRevisionForAppVersion(TestConstants.TEST_STUDY, SCHEMA_ID,
                clientInfo);
        assertEquals(2, retval.getRevision());
    }

    @Test
    public void getLatestMatchOld() {
        setupDaoForGetLatest();

        // make client info
        ClientInfo clientInfo = new ClientInfo.Builder().withOsName(OS_NAME).withAppVersion(15).build();

        // execute and validate
        UploadSchema retval = svc.getLatestUploadSchemaRevisionForAppVersion(TestConstants.TEST_STUDY, SCHEMA_ID,
                clientInfo);
        assertEquals(1, retval.getRevision());
    }

    @Test
    public void getLatestMatchNone() {
        setupDaoForGetLatest();

        // make client info
        ClientInfo clientInfo = new ClientInfo.Builder().withOsName(OS_NAME).withAppVersion(5).build();

        // execute and validate
        UploadSchema retval = svc.getLatestUploadSchemaRevisionForAppVersion(TestConstants.TEST_STUDY, SCHEMA_ID,
                clientInfo);
        assertNull(retval);
    }

    private void setupDaoForGetLatest() {
        // Two schemas, rev 1 has min=10. Rev 2 has min=20.
        UploadSchema schemaRev1 = makeSimpleSchema();
        schemaRev1.setRevision(1);
        schemaRev1.setMinAppVersion(OS_NAME, 10);

        UploadSchema schemaRev2 = makeSimpleSchema();
        schemaRev2.setRevision(2);
        schemaRev2.setMinAppVersion(OS_NAME, 20);

        // mock dao
        when(dao.getUploadSchemaAllRevisionsById(TestConstants.TEST_STUDY, SCHEMA_ID)).thenReturn(ImmutableList.of(
                schemaRev1, schemaRev2));
    }

    @Test
    public void isSchemaAvailableForClientInfo() {
        // test cases: { clientInfoAppVersion, minAppVersion, maxAppVersion, expected }
        Object[][] testCaseArray = {
                { null, null, null, true },
                { null, 10, 20, true },
                { 15, null, null, true },
                { 5, 10, null, false },
                { 15, 10, null, true },
                { 15, null, 20, true },
                { 25, null, 20, false },
                { 5, 10, 20, false },
                { 15, 10, 20, true },
                { 25, 10, 20, false },
        };

        for (Object[] oneTestCase : testCaseArray) {
            // test args
            Integer clientInfoAppVersion = (Integer) oneTestCase[0];
            Integer minAppVersion = (Integer) oneTestCase[1];
            Integer maxAppVersion = (Integer) oneTestCase[2];
            boolean expected = (boolean) oneTestCase[3];

            // set up test
            ClientInfo clientInfo = new ClientInfo.Builder().withOsName(OS_NAME).withAppVersion(clientInfoAppVersion)
                    .build();

            UploadSchema schema = makeSimpleSchema();
            schema.setMinAppVersion(OS_NAME, minAppVersion);
            schema.setMaxAppVersion(OS_NAME, maxAppVersion);

            // execute and validate
            boolean retval = UploadSchemaService.isSchemaAvailableForClientInfo(schema, clientInfo);
            assertEquals(expected, retval);
        }
    }

    @Test(expected = BadRequestException.class)
    public void updateV4NullId() {
        svc.updateSchemaRevisionV4(TestConstants.TEST_STUDY, null, SCHEMA_REV, svcInputSchema);
    }

    @Test(expected = BadRequestException.class)
    public void updateV4EmptyId() {
        svc.updateSchemaRevisionV4(TestConstants.TEST_STUDY, "", SCHEMA_REV, svcInputSchema);
    }

    @Test(expected = BadRequestException.class)
    public void updateV4BlankId() {
        svc.updateSchemaRevisionV4(TestConstants.TEST_STUDY, "   ", SCHEMA_REV, svcInputSchema);
    }

    @Test(expected = BadRequestException.class)
    public void updateV4NegativeRev() {
        svc.updateSchemaRevisionV4(TestConstants.TEST_STUDY, SCHEMA_ID, -1, svcInputSchema);
    }

    @Test(expected = BadRequestException.class)
    public void updateV4ZeroRev() {
        svc.updateSchemaRevisionV4(TestConstants.TEST_STUDY, SCHEMA_ID, 0, svcInputSchema);
    }

    @Test(expected = EntityNotFoundException.class)
    public void updateV4NotFound() {
        // mock dao to return null
        when(dao.getUploadSchemaByIdAndRevision(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV)).thenReturn(null);

        svc.updateSchemaRevisionV4(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV, svcInputSchema);
    }

    @Test
    public void updateV4InvalidSchema() {
        // mock dao output - Must have a schema to update or we'll throw a 404.
        when(dao.getUploadSchemaByIdAndRevision(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV)).thenReturn(
                makeSimpleSchema());

        // execute test
        try {
            svc.updateSchemaRevisionV4(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV, makeInvalidSchema());
            fail("expected exception");
        } catch (InvalidEntityException ex) {
            assertSchemaValidationException(ex);
        }
    }

    @Test
    public void updateV4IncompatibleChange() {
        // This update fails for 4 reasons
        // - deleted fields
        // - modified non-compatible field
        // - added required fields
        // - modified schema type

        // Make old schema - Only field def list and schema type matter for this test.
        List<UploadFieldDefinition> oldFieldDefList = ImmutableList.of(
                new UploadFieldDefinition.Builder().withName("always").withType(UploadFieldType.BOOLEAN).build(),
                new UploadFieldDefinition.Builder().withName("delete-me-1").withType(UploadFieldType.BOOLEAN)
                        .build(),
                new UploadFieldDefinition.Builder().withName("delete-me-2").withType(UploadFieldType.INT)
                        .build(),
                new UploadFieldDefinition.Builder().withName("modify-me-1").withType(UploadFieldType.STRING)
                        .withUnboundedText(true).build(),
                new UploadFieldDefinition.Builder().withName("modify-me-2").withType(UploadFieldType.STRING)
                        .withUnboundedText(true).build());

        UploadSchema oldSchema = makeSimpleSchema();
        oldSchema.setSchemaType(UploadSchemaType.IOS_DATA);
        oldSchema.setFieldDefinitions(oldFieldDefList);

        // Make new schema
        List<UploadFieldDefinition> newFieldDefList = ImmutableList.of(
                new UploadFieldDefinition.Builder().withName("always").withType(UploadFieldType.BOOLEAN).build(),
                new UploadFieldDefinition.Builder().withName("modify-me-1").withType(UploadFieldType.STRING)
                        .withMaxLength(24).build(),
                new UploadFieldDefinition.Builder().withName("modify-me-2").withType(UploadFieldType.FLOAT)
                        .withMaxLength(24).build(),
                new UploadFieldDefinition.Builder().withName("add-me-1").withType(UploadFieldType.BOOLEAN)
                        .withRequired(true).build(),
                new UploadFieldDefinition.Builder().withName("add-me-2").withType(UploadFieldType.BOOLEAN)
                        .withRequired(true).build());

        UploadSchema newSchema = makeSimpleSchema();
        newSchema.setSchemaType(UploadSchemaType.IOS_SURVEY);
        newSchema.setFieldDefinitions(newFieldDefList);

        // mock dao
        when(dao.getUploadSchemaByIdAndRevision(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV)).thenReturn(
                oldSchema);

        // execute and validate
        String errMsg = null;
        try {
            svc.updateSchemaRevisionV4(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV, newSchema);
            fail("expected exception");
        } catch (BadRequestException ex) {
            errMsg = ex.getMessage();
        }

        assertTrue(errMsg.contains("Added fields must be optional: add-me-1, add-me-2"));
        assertTrue(errMsg.contains("Can't delete fields: delete-me-1, delete-me-2"));
        assertTrue(errMsg.contains("Incompatible changes to fields: modify-me-1, modify-me-2"));
        assertTrue(errMsg.contains("Can't modify schema type, old=IOS_DATA, new=IOS_SURVEY"));

        // verify no calls to dao
        verify(dao, never()).updateSchemaRevision(any());
    }

    @Test
    public void updateV4Success() {
        // Test update with
        // - unchanged field
        // - modified compatible field
        // - added optional field

        // Make old schema - Only field def list and schema type matter for this test.
        List<UploadFieldDefinition> oldFieldDefList = ImmutableList.of(
                new UploadFieldDefinition.Builder().withName("always").withType(UploadFieldType.BOOLEAN).build(),
                new UploadFieldDefinition.Builder().withName("modify-me").withType(UploadFieldType.MULTI_CHOICE)
                        .withMultiChoiceAnswerList("foo", "bar").withAllowOtherChoices(false).build());

        UploadSchema oldSchema = makeSimpleSchema();
        oldSchema.setSchemaType(UploadSchemaType.IOS_SURVEY);
        oldSchema.setFieldDefinitions(oldFieldDefList);

        // Make new schema
        List<UploadFieldDefinition> newFieldDefList = ImmutableList.of(
                new UploadFieldDefinition.Builder().withName("always").withType(UploadFieldType.BOOLEAN).build(),
                new UploadFieldDefinition.Builder().withName("modify-me").withType(UploadFieldType.MULTI_CHOICE)
                        .withMultiChoiceAnswerList("foo", "bar", "baz").withAllowOtherChoices(true).build(),
                new UploadFieldDefinition.Builder().withName("added-optional-field")
                        .withType(UploadFieldType.BOOLEAN).withRequired(false).build());

        UploadSchema newSchema = makeSimpleSchema();
        newSchema.setSchemaType(UploadSchemaType.IOS_SURVEY);
        newSchema.setFieldDefinitions(newFieldDefList);

        // mock dao
        when(dao.getUploadSchemaByIdAndRevision(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV)).thenReturn(
                oldSchema);

        ArgumentCaptor<UploadSchema> daoInputSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        UploadSchema daoOutputSchema = UploadSchema.create();
        when(dao.updateSchemaRevision(daoInputSchemaCaptor.capture())).thenReturn(daoOutputSchema);

        // execute
        UploadSchema svcOutputSchema = svc.updateSchemaRevisionV4(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV,
                newSchema);

        // Validate we set key parameters when passing the schema to the DAO, including study ID, schema ID, and rev.
        UploadSchema daoInputSchema = daoInputSchemaCaptor.getValue();
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, daoInputSchema.getStudyId());
        assertEquals(SCHEMA_ID, daoInputSchema.getSchemaId());
        assertEquals(SCHEMA_REV, daoInputSchema.getRevision());

        // Validate DAO input is also svcOutput.
        assertSame(daoOutputSchema, svcOutputSchema);
    }

    private static UploadSchema makeSimpleSchema() {
        UploadSchema schema = UploadSchema.create();
        schema.setFieldDefinitions(FIELD_DEF_LIST);
        schema.setName(SCHEMA_NAME);
        schema.setSchemaId(SCHEMA_ID);
        schema.setSchemaType(UploadSchemaType.IOS_DATA);
        return schema;
    }

    // Make an invalid schema to test validation. This schema will be missing lots of required fields (like name and
    // schema type) and will contain a single field def with no fields.
    private static UploadSchema makeInvalidSchema() {
        // We'll need a schema ID anyway. Otherwise, the request will be rejected with a 400 Bad Request.
        UploadSchema schema = UploadSchema.create();
        schema.setSchemaId(SCHEMA_ID);
        schema.setFieldDefinitions(ImmutableList.of(new UploadFieldDefinition.Builder().build()));
        return schema;
    }

    // Validate that the errors throw for a schema made from makeInvalidSchema()
    private static void assertSchemaValidationException(InvalidEntityException ex) {
        assertEquals("name is required", ex.getErrors().get("name").get(0));
        assertEquals("schemaType is required", ex.getErrors().get("schemaType").get(0));
        assertEquals("fieldDefinitions[0].name is required", ex.getErrors().get("fieldDefinitions[0].name")
                .get(0));
        assertEquals("fieldDefinitions[0].type is required", ex.getErrors().get("fieldDefinitions[0].type")
                .get(0));
    }
}
