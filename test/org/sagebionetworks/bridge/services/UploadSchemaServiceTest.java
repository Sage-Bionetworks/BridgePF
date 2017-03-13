package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.UploadSchemaDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
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

    @Test(expected = InvalidEntityException.class)
    public void createV4InvalidSchema() {
        // Simple invalid schema, like one with no fields.
        new UploadSchemaService().createSchemaRevisionV4(TestConstants.TEST_STUDY, UploadSchema.create());
    }

    @Test
    public void createV4Success() {
        // mock dao
        UploadSchema inputSchema = makeSimpleSchema();
        UploadSchema outputSchema = UploadSchema.create();
        UploadSchemaDao dao = mock(UploadSchemaDao.class);
        when(dao.createSchemaRevisionV4(TestConstants.TEST_STUDY, inputSchema)).thenReturn(outputSchema);

        // execute and validate
        UploadSchemaService svc = new UploadSchemaService();
        svc.setUploadSchemaDao(dao);
        UploadSchema retVal = svc.createSchemaRevisionV4(TestConstants.TEST_STUDY, inputSchema);
        assertSame(outputSchema, retVal);
    }

    @Test(expected = InvalidEntityException.class)
    public void createNullSchema() {
        new UploadSchemaService().createOrUpdateUploadSchema(makeTestStudy(), null);
    }

    // We don't need to exhaust all validation possibilities, since that's already tested by the UploadSchema.Validator
    // tests. We just need to test that validation happens at all.
    @Test(expected = InvalidEntityException.class)
    public void createInvalidSchema() {
        new UploadSchemaService().createOrUpdateUploadSchema(makeTestStudy(), UploadSchema.create());
    }

    // Since UploadSchemaService is just a call through to the DAO, verify the input value is passed to the DAO, and
    // that the value returned by the DAO is returned by the service.
    @Test
    public void createSchemaSuccess() {
        // create valid schema
        UploadSchema schema = UploadSchema.create();
        schema.setName("happy schema");
        schema.setSchemaId("happy-schema");
        schema.setSchemaType(UploadSchemaType.IOS_DATA);

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("test-field")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build());
        schema.setFieldDefinitions(fieldDefList);

        // mock dao
        UploadSchema daoRetVal = UploadSchema.create();
        UploadSchemaDao mockDao = mock(UploadSchemaDao.class);
        when(mockDao.createOrUpdateUploadSchema("test-study", schema)).thenReturn(daoRetVal);

        // execute and validate
        UploadSchemaService svc = new UploadSchemaService();
        svc.setUploadSchemaDao(mockDao);
        UploadSchema svcRetVal = svc.createOrUpdateUploadSchema(makeTestStudy(), schema);
        assertSame(daoRetVal, svcRetVal);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAndRevNullId() {
        new UploadSchemaService().deleteUploadSchemaByIdAndRev(makeTestStudy(), null, 1);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAndRevEmptyId() {
        new UploadSchemaService().deleteUploadSchemaByIdAndRev(makeTestStudy(), "", 1);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAndRevBlankId() {
        new UploadSchemaService().deleteUploadSchemaByIdAndRev(makeTestStudy(), "   ", 1);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAndRevNegativeRev() {
        new UploadSchemaService().deleteUploadSchemaByIdAndRev(makeTestStudy(), "delete-schema", -1);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAndRevZeroRev() {
        new UploadSchemaService().deleteUploadSchemaByIdAndRev(makeTestStudy(), "delete-schema", 0);
    }

    @Test
    public void deleteByIdAndRevSuccess() {
        // mock dao
        UploadSchemaDao mockDao = mock(UploadSchemaDao.class);
        UploadSchemaService svc = new UploadSchemaService();
        svc.setUploadSchemaDao(mockDao);

        // execute and verify delete call
        StudyIdentifier studyIdentifier = makeTestStudy();
        svc.deleteUploadSchemaByIdAndRev(studyIdentifier, "delete-schema", 1);
        verify(mockDao).deleteUploadSchemaByIdAndRev(studyIdentifier, "delete-schema", 1);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdNullId() {
        new UploadSchemaService().deleteUploadSchemaById(makeTestStudy(), null);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdEmptyId() {
        new UploadSchemaService().deleteUploadSchemaById(makeTestStudy(), "");
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdBlankId() {
        new UploadSchemaService().deleteUploadSchemaById(makeTestStudy(), "   ");
    }

    @Test
    public void deleteByIdSuccess() {
        // mock dao
        UploadSchemaDao mockDao = mock(UploadSchemaDao.class);
        UploadSchemaService svc = new UploadSchemaService();
        svc.setUploadSchemaDao(mockDao);

        // execute and verify delete call
        StudyIdentifier studyIdentifier = makeTestStudy();
        svc.deleteUploadSchemaById(studyIdentifier, "delete-schema");
        verify(mockDao).deleteUploadSchemaById(studyIdentifier, "delete-schema");
    }

    @Test
    public void getLatestMatchMultiple() {
        // Setup dao and service.
        UploadSchemaDao mockDao = setupDaoForGetLatest();
        UploadSchemaService service = new UploadSchemaService();
        service.setUploadSchemaDao(mockDao);

        // make client info
        ClientInfo clientInfo = new ClientInfo.Builder().withOsName(OS_NAME).withAppVersion(25).build();

        // execute and validate
        UploadSchema retval = service.getLatestUploadSchemaRevisionForAppVersion(TestConstants.TEST_STUDY, SCHEMA_ID,
                clientInfo);
        assertEquals(2, retval.getRevision());
    }

    @Test
    public void getLatestMatchOld() {
        // Setup dao and service.
        UploadSchemaDao mockDao = setupDaoForGetLatest();
        UploadSchemaService service = new UploadSchemaService();
        service.setUploadSchemaDao(mockDao);

        // make client info
        ClientInfo clientInfo = new ClientInfo.Builder().withOsName(OS_NAME).withAppVersion(15).build();

        // execute and validate
        UploadSchema retval = service.getLatestUploadSchemaRevisionForAppVersion(TestConstants.TEST_STUDY, SCHEMA_ID,
                clientInfo);
        assertEquals(1, retval.getRevision());
    }

    @Test
    public void getLatestMatchNone() {
        // Setup dao and service.
        UploadSchemaDao mockDao = setupDaoForGetLatest();
        UploadSchemaService service = new UploadSchemaService();
        service.setUploadSchemaDao(mockDao);

        // make client info
        ClientInfo clientInfo = new ClientInfo.Builder().withOsName(OS_NAME).withAppVersion(5).build();

        // execute and validate
        UploadSchema retval = service.getLatestUploadSchemaRevisionForAppVersion(TestConstants.TEST_STUDY, SCHEMA_ID,
                clientInfo);
        assertNull(retval);
    }

    private static UploadSchemaDao setupDaoForGetLatest() {
        // Two schemas, rev 1 has min=10. Rev 2 has min=20.
        UploadSchema schemaRev1 = makeSimpleSchema();
        schemaRev1.setRevision(1);
        schemaRev1.setMinAppVersion(OS_NAME, 10);

        UploadSchema schemaRev2 = makeSimpleSchema();
        schemaRev2.setRevision(2);
        schemaRev2.setMinAppVersion(OS_NAME, 20);

        // mock dao
        UploadSchemaDao mockDao = mock(UploadSchemaDao.class);
        when(mockDao.getUploadSchemaAllRevisions(TestConstants.TEST_STUDY, SCHEMA_ID)).thenReturn(ImmutableList.of(
                schemaRev1, schemaRev2));
        return mockDao;
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
    public void getNullSchemaId() {
        new UploadSchemaService().getUploadSchema(makeTestStudy(), null);
    }

    @Test(expected = BadRequestException.class)
    public void getEmptySchemaId() {
        new UploadSchemaService().getUploadSchema(makeTestStudy(), "");
    }

    @Test(expected = BadRequestException.class)
    public void getBlankSchemaId() {
        new UploadSchemaService().getUploadSchema(makeTestStudy(), "   ");
    }

    // Similarly, verify that the DAO receives and returns the right objects.
    @Test
    public void getSchemaSuccess() {
        // mock dao
        UploadSchema daoRetVal = UploadSchema.create();
        UploadSchemaDao mockDao = mock(UploadSchemaDao.class);
        when(mockDao.getUploadSchema("test-study", "test-schema")).thenReturn(daoRetVal);

        // execute and validate
        UploadSchemaService svc = new UploadSchemaService();
        svc.setUploadSchemaDao(mockDao);
        UploadSchema svcRetVal = svc.getUploadSchema(makeTestStudy(), "test-schema");
        assertSame(daoRetVal, svcRetVal);
    }

    @Test(expected = BadRequestException.class)
    public void getByIdAndRevNullId() {
        new UploadSchemaService().getUploadSchemaByIdAndRev(makeTestStudy(), null, 1);
    }

    @Test(expected = BadRequestException.class)
    public void getByIdAndRevEmptyId() {
        new UploadSchemaService().getUploadSchemaByIdAndRev(makeTestStudy(), "", 1);
    }

    @Test(expected = BadRequestException.class)
    public void getByIdAndRevBlankId() {
        new UploadSchemaService().getUploadSchemaByIdAndRev(makeTestStudy(), "   ", 1);
    }

    @Test(expected = BadRequestException.class)
    public void getByIdAndRevNegativeRev() {
        new UploadSchemaService().getUploadSchemaByIdAndRev(makeTestStudy(), "test-schema-rev", -1);
    }

    @Test(expected = BadRequestException.class)
    public void getByIdAndRevZeroRev() {
        new UploadSchemaService().getUploadSchemaByIdAndRev(makeTestStudy(), "test-schema-rev", 0);
    }

    @Test
    public void getByIdAndRevSuccess() {
        // mock dao
        StudyIdentifier studyIdentifier = makeTestStudy();
        UploadSchema daoRetVal = UploadSchema.create();
        UploadSchemaDao mockDao = mock(UploadSchemaDao.class);
        when(mockDao.getUploadSchemaByIdAndRev(studyIdentifier, "test-schema-rev", 1)).thenReturn(daoRetVal);

        // execute and validate
        UploadSchemaService svc = new UploadSchemaService();
        svc.setUploadSchemaDao(mockDao);
        UploadSchema svcRetVal = svc.getUploadSchemaByIdAndRev(makeTestStudy(), "test-schema-rev", 1);
        assertSame(daoRetVal, svcRetVal);
    }

    @Test
    public void getSchemasForStudy() {
        // mock dao
        StudyIdentifier studyIdentifier = makeTestStudy();
        
        List<UploadSchema> daoRetVal = ImmutableList.of(UploadSchema.create());
        UploadSchemaDao mockDao = mock(UploadSchemaDao.class);
        when(mockDao.getUploadSchemasForStudy(studyIdentifier)).thenReturn(daoRetVal);

        // execute and validate
        UploadSchemaService svc = new UploadSchemaService();
        svc.setUploadSchemaDao(mockDao);
        List<UploadSchema> svcRetVal = svc.getUploadSchemasForStudy(studyIdentifier);
        assertSame(daoRetVal, svcRetVal);
    }
    
    @Test
    public void getSchemaAllRevisions() {
        String schemaId = "test-schema";
        
        // mock dao
        StudyIdentifier studyIdentifier = makeTestStudy();
        
        UploadSchema schema1 = UploadSchema.create();
        schema1.setRevision(3);
        UploadSchema schema2 = UploadSchema.create();
        schema1.setRevision(2);
        UploadSchema schema3 = UploadSchema.create();
        schema1.setRevision(1);
        
        List<UploadSchema> daoRetVal = ImmutableList.of(schema1, schema2, schema3);
        UploadSchemaDao mockDao = mock(UploadSchemaDao.class);
        when(mockDao.getUploadSchemaAllRevisions(studyIdentifier, schemaId)).thenReturn(daoRetVal);

        // execute and validate
        UploadSchemaService svc = new UploadSchemaService();
        svc.setUploadSchemaDao(mockDao);
        List<UploadSchema> svcRetVal = svc.getUploadSchemaAllRevisions(studyIdentifier, schemaId);
        assertSame(daoRetVal, svcRetVal);
    }

    @Test(expected = BadRequestException.class)
    public void updateV4NullId() {
        new UploadSchemaService().updateSchemaRevisionV4(TestConstants.TEST_STUDY, null, SCHEMA_REV,
                makeSimpleSchema());
    }

    @Test(expected = BadRequestException.class)
    public void updateV4EmptyId() {
        new UploadSchemaService().updateSchemaRevisionV4(TestConstants.TEST_STUDY, "", SCHEMA_REV, makeSimpleSchema());
    }

    @Test(expected = BadRequestException.class)
    public void updateV4BlankId() {
        new UploadSchemaService().updateSchemaRevisionV4(TestConstants.TEST_STUDY, "   ", SCHEMA_REV,
                makeSimpleSchema());
    }

    @Test(expected = BadRequestException.class)
    public void updateV4NegativeRev() {
        new UploadSchemaService().updateSchemaRevisionV4(TestConstants.TEST_STUDY, SCHEMA_ID, -1, makeSimpleSchema());
    }

    @Test(expected = BadRequestException.class)
    public void updateV4ZeroRev() {
        new UploadSchemaService().updateSchemaRevisionV4(TestConstants.TEST_STUDY, SCHEMA_ID, 0, makeSimpleSchema());
    }

    @Test(expected = InvalidEntityException.class)
    public void updateV4InvalidSchema() {
        // Simple invalid schema, like one with no fields.
        new UploadSchemaService().updateSchemaRevisionV4(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV,
                UploadSchema.create());
    }

    @Test
    public void updateV4Success() {
        // mock dao
        UploadSchema inputSchema = makeSimpleSchema();
        UploadSchema outputSchema = UploadSchema.create();
        UploadSchemaDao dao = mock(UploadSchemaDao.class);
        when(dao.updateSchemaRevisionV4(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV, inputSchema)).thenReturn(
                outputSchema);

        // execute and validate
        UploadSchemaService svc = new UploadSchemaService();
        svc.setUploadSchemaDao(dao);
        UploadSchema retVal = svc.updateSchemaRevisionV4(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV, inputSchema);
        assertSame(outputSchema, retVal);
    }

    private static StudyIdentifier makeTestStudy() {
        return new StudyIdentifierImpl("test-study");
    }

    private static UploadSchema makeSimpleSchema() {
        UploadSchema schema = UploadSchema.create();
        schema.setFieldDefinitions(FIELD_DEF_LIST);
        schema.setName(SCHEMA_NAME);
        schema.setSchemaId(SCHEMA_ID);
        schema.setSchemaType(UploadSchemaType.IOS_DATA);
        return schema;
    }
}
