package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import org.sagebionetworks.bridge.dao.UploadSchemaDao;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadFieldDefinition;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;

public class UploadSchemaServiceTest {
    @Test(expected = InvalidEntityException.class)
    public void createNullSchema() {
        new UploadSchemaService().createOrUpdateUploadSchema(makeTestStudy(), null);
    }

    // We don't need to exhaust all validation possibilities, since that's already tested by the UploadSchema.Validator
    // tests. We just need to test that validation happens at all.
    @Test(expected = InvalidEntityException.class)
    public void createInvalidSchema() {
        new UploadSchemaService().createOrUpdateUploadSchema(makeTestStudy(), new DynamoUploadSchema());
    }

    // Since UploadSchemaService is just a call through to the DAO, verify the input value is passed to the DAO, and
    // that the value returned by the DAO is returned by the service.
    @Test
    public void createSchemaSuccess() {
        // create valid schema
        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setName("happy schema");
        schema.setSchemaId("happy-schema");
        schema.setSchemaType(UploadSchemaType.IOS_DATA);

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new DynamoUploadFieldDefinition.Builder().withName("test-field")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build());
        schema.setFieldDefinitions(fieldDefList);

        // mock dao
        UploadSchema daoRetVal = new DynamoUploadSchema();
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
        UploadSchema daoRetVal = new DynamoUploadSchema();
        UploadSchemaDao mockDao = mock(UploadSchemaDao.class);
        when(mockDao.getUploadSchema("test-study", "test-schema")).thenReturn(daoRetVal);

        // execute and validate
        UploadSchemaService svc = new UploadSchemaService();
        svc.setUploadSchemaDao(mockDao);
        UploadSchema svcRetVal = svc.getUploadSchema(makeTestStudy(), "test-schema");
        assertSame(daoRetVal, svcRetVal);
    }

    @Test
    public void getSchemasForStudy() {
        // mock dao
        StudyIdentifier studyIdentifier = makeTestStudy();
        List<UploadSchema> daoRetVal = ImmutableList.<UploadSchema>of(new DynamoUploadSchema());
        UploadSchemaDao mockDao = mock(UploadSchemaDao.class);
        when(mockDao.getUploadSchemasForStudy(studyIdentifier)).thenReturn(daoRetVal);

        // execute and validate
        UploadSchemaService svc = new UploadSchemaService();
        svc.setUploadSchemaDao(mockDao);
        List<UploadSchema> svcRetVal = svc.getUploadSchemasForStudy(studyIdentifier);
        assertSame(daoRetVal, svcRetVal);
    }

    private static StudyIdentifier makeTestStudy() {
        return new StudyIdentifierImpl("test-study");
    }
}
