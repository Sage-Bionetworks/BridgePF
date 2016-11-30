package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.LocalDate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoHealthDataDaoDdbTest {
    private static final long CREATED_ON = 1424136378727L;
    private static final String DATA_TEXT = "{\"data\":\"dummy value\"}";
    private static final String METADATA_TEXT = "{\"metadata\":\"dummy meta value\"}";
    private static final String SCHEMA_ID = "test-schema-id";
    private static final int SCHEMA_REV = 2;
    private static final LocalDate UPLOAD_DATE = LocalDate.parse("2016-05-06");
    private static final String UPLOAD_ID = "upload-id";
    private static final long UPLOADED_ON = 1462575525894L;
    private static final String USER_EXTERNAL_ID = "external-id";

    private static final String HEALTH_CODE_2 = "test-healthcode-2";
    private static final String SCHEMA_ID_2 = "test-schema-id-2";

    private String healthCode;
    private String recordId;

    private DynamoHealthDataRecord testRecord;

    @Resource(name = "healthDataDdbMapper")
    private DynamoDBMapper mapper;

    @Autowired
    private DynamoHealthDataDao dao;

    @Before
    public void setup() throws IOException {
        healthCode = TestUtils.randomName(DynamoHealthDataDaoDdbTest.class);
        recordId = TestUtils.randomName(DynamoHealthDataDaoDdbTest.class);

        // create test record and save it
        testRecord = new DynamoHealthDataRecord();
        testRecord.setCreatedOn(CREATED_ON);
        testRecord.setHealthCode(healthCode);
        testRecord.setSchemaId(SCHEMA_ID);
        testRecord.setId(recordId);
        testRecord.setSchemaRevision(SCHEMA_REV);
        testRecord.setUploadDate(UPLOAD_DATE);
        testRecord.setUploadedOn(UPLOADED_ON);
        testRecord.setUploadId(UPLOAD_ID);
        testRecord.setUserSharingScope(ParticipantOption.SharingScope.SPONSORS_AND_PARTNERS);
        testRecord.setUserExternalId(USER_EXTERNAL_ID);
        testRecord.setUserDataGroups(TestConstants.USER_DATA_GROUPS);
        testRecord.setData(BridgeObjectMapper.get().readTree(DATA_TEXT));
        testRecord.setMetadata(BridgeObjectMapper.get().readTree(METADATA_TEXT));
        testRecord.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);

        mapper.save(testRecord);
    }

    @After
    public void cleanup() {
        mapper.delete(testRecord);
    }

    @Test
    public void ddbRoundtrip() throws Exception {
        DynamoHealthDataRecord savedRecord = mapper.load(testRecord);

        // validate fields
        assertEquals(CREATED_ON, savedRecord.getCreatedOn().longValue());
        assertEquals(healthCode, savedRecord.getHealthCode());
        assertEquals(recordId, savedRecord.getId());
        assertEquals(SCHEMA_ID, savedRecord.getSchemaId());
        assertEquals(SCHEMA_REV, savedRecord.getSchemaRevision());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, savedRecord.getStudyId());
        assertEquals(UPLOAD_DATE, savedRecord.getUploadDate());
        assertEquals(UPLOAD_ID, savedRecord.getUploadId());
        assertEquals(UPLOADED_ON, savedRecord.getUploadedOn().longValue());
        assertEquals(ParticipantOption.SharingScope.SPONSORS_AND_PARTNERS, savedRecord.getUserSharingScope());
        assertEquals(USER_EXTERNAL_ID, savedRecord.getUserExternalId());
        assertEquals(TestConstants.USER_DATA_GROUPS, savedRecord.getUserDataGroups());
        assertEquals(1L, savedRecord.getVersion().longValue());

        JsonNode dataNode = savedRecord.getData();
        assertEquals(1, dataNode.size());
        assertEquals("dummy value", dataNode.get("data").textValue());

        JsonNode metadataNode = savedRecord.getMetadata();
        assertEquals(1, metadataNode.size());
        assertEquals("dummy meta value", metadataNode.get("metadata").textValue());

    }

    @Test
    public void getRecordsByHealthCodeCreatedOnSchemaId() throws IOException {
        // query that record
        List<HealthDataRecord> retList = dao.getRecordsByHealthCodeCreatedOnSchemaId(healthCode, CREATED_ON, SCHEMA_ID);

        // verify
        assertNotNull(retList);
        assert(retList.size() == 1);
        assertNotNull(retList.get(0));
        HealthDataRecord savedRecord = retList.get(0);

        // validate fields
        assertEquals(CREATED_ON, savedRecord.getCreatedOn().longValue());
        assertEquals(healthCode, savedRecord.getHealthCode());
        assertEquals(recordId, savedRecord.getId());
        assertEquals(SCHEMA_ID, savedRecord.getSchemaId());
        assertEquals(SCHEMA_REV, savedRecord.getSchemaRevision());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, savedRecord.getStudyId());
        assertEquals(UPLOAD_DATE, savedRecord.getUploadDate());
        assertEquals(UPLOAD_ID, savedRecord.getUploadId());
        assertEquals(UPLOADED_ON, savedRecord.getUploadedOn().longValue());
        assertEquals(ParticipantOption.SharingScope.SPONSORS_AND_PARTNERS, savedRecord.getUserSharingScope());
        assertEquals(USER_EXTERNAL_ID, savedRecord.getUserExternalId());
        assertEquals(TestConstants.USER_DATA_GROUPS, savedRecord.getUserDataGroups());

        JsonNode dataNode = savedRecord.getData();
        assertEquals(1, dataNode.size());
        assertEquals("dummy value", dataNode.get("data").textValue());

        JsonNode metadataNode = savedRecord.getMetadata();
        assertEquals(1, metadataNode.size());
        assertEquals("dummy meta value", metadataNode.get("metadata").textValue());

    }

    @Test
    public void differentHealthcodeForgetRecordsByHealthCodeCreatedOnSchemaId() {
        // query from a different healthcode
        List<HealthDataRecord> retList = dao.getRecordsByHealthCodeCreatedOnSchemaId(HEALTH_CODE_2, CREATED_ON, SCHEMA_ID);

        // verify - should not have anything in the list
        assertNotNull(retList);
        assertEquals(retList.size(), 0);
    }

    @Test
    public void differentSchemaIdForgetRecordsByHealthCodeCreatedOnSchemaId() {
        // query from a different schemaId
        List<HealthDataRecord> retList = dao.getRecordsByHealthCodeCreatedOnSchemaId(healthCode, CREATED_ON, SCHEMA_ID_2);

        // verify - should not have anything in the list
        assertNotNull(retList);
        assertEquals(retList.size(), 0);
    }

    @Test
    public void createdOnOutOfRangeForgetRecordsByHealthCodeCreatedOnSchemaId() {
        // query from a createdOn value out of range
        List<HealthDataRecord> retList = dao.getRecordsByHealthCodeCreatedOnSchemaId(healthCode, CREATED_ON + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS) + 1L, SCHEMA_ID);

        // verify - should not have anything in the list
        assertNotNull(retList);
        assertEquals(retList.size(), 0);
    }

    @Test
    public void createdOnInRangeForgetRecordsByHealthCodeCreatedOnSchemaId() {
        // query from a createdOn value out of range
        List<HealthDataRecord> retList = dao.getRecordsByHealthCodeCreatedOnSchemaId(healthCode, CREATED_ON + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS), SCHEMA_ID);

        // verify - should not have anything in the list
        assertNotNull(retList);
        assertNotEquals(retList.size(), 0);
    }
}
