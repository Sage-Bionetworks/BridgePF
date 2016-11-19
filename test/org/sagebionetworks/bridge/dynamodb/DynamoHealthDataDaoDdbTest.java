package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.LocalDate;
import org.junit.After;
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

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoHealthDataDaoDdbTest {
    private static final long CREATED_ON = 1424136378727L;
    private static final String HEALTH_CODE = TestUtils.randomName(DynamoHealthDataDaoDdbTest.class);
    private static final String RECORD_ID = TestUtils.randomName(DynamoHealthDataDaoDdbTest.class);
    private static final String DATA_TEXT = "{\"data\":\"dummy value\"}";
    private static final String METADATA_TEXT = "{\"metadata\":\"dummy meta value\"}";
    private static final String SCHEMA_ID = TestUtils.randomName(DynamoHealthDataDaoDdbTest.class);
    private static final int SCHEMA_REV = 2;
    private static final LocalDate UPLOAD_DATE = LocalDate.parse("2016-05-06");
    private static final String UPLOAD_ID = "upload-id";
    private static final long UPLOADED_ON = 1462575525894L;
    private static final String USER_EXTERNAL_ID = "external-id";

    @Resource(name = "healthDataDdbMapper")
    private DynamoDBMapper mapper;

    @Autowired
    private DynamoHealthDataDao dao;

    @Test
    public void ddbRoundtrip() throws Exception {
        // create test record with all fields
        DynamoHealthDataRecord record = new DynamoHealthDataRecord();
        record.setCreatedOn(CREATED_ON);
        record.setData(BridgeObjectMapper.get().readTree(DATA_TEXT));
        record.setHealthCode(HEALTH_CODE);
        record.setId(RECORD_ID);
        record.setMetadata(BridgeObjectMapper.get().readTree(METADATA_TEXT));
        record.setSchemaId(SCHEMA_ID);
        record.setSchemaRevision(SCHEMA_REV);
        record.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        record.setUploadDate(UPLOAD_DATE);
        record.setUploadId(UPLOAD_ID);
        record.setUploadedOn(UPLOADED_ON);
        record.setUserSharingScope(ParticipantOption.SharingScope.SPONSORS_AND_PARTNERS);
        record.setUserExternalId(USER_EXTERNAL_ID);
        record.setUserDataGroups(TestConstants.USER_DATA_GROUPS);

        // Save it to DDB, then read it back and make sure it has the same fields. (Can't use .equals(), even if we
        // implemented it, because the mapper functions modify the object we pass in.)
        mapper.save(record);
        DynamoHealthDataRecord savedRecord = mapper.load(record);

        try {
            // validate fields
            assertEquals(CREATED_ON, savedRecord.getCreatedOn().longValue());
            assertEquals(HEALTH_CODE, savedRecord.getHealthCode());
            assertEquals(RECORD_ID, savedRecord.getId());
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
        } finally {
            // cleanup
            mapper.delete(savedRecord);
        }
    }

    @Test
    public void getRecordsByHealthCodeCreatedOnSchemaId() throws IOException {
        // store a test healthdata record in ddb
        DynamoHealthDataRecord testRecord = new DynamoHealthDataRecord();
        testRecord.setCreatedOn(CREATED_ON);
        testRecord.setHealthCode(HEALTH_CODE);
        testRecord.setSchemaId(SCHEMA_ID);
        testRecord.setId(RECORD_ID);
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

        // query that record
        List<HealthDataRecord> retList = dao.getRecordsByHealthCodeCreatedOnSchemaId(HEALTH_CODE, CREATED_ON, SCHEMA_ID);

        // verify
        try {
            assertNotNull(retList);
            assert(retList.size() == 1);
            assertNotNull(retList.get(0));
            HealthDataRecord savedRecord = retList.get(0);

            // validate fields
            assertEquals(CREATED_ON, savedRecord.getCreatedOn().longValue());
            assertEquals(HEALTH_CODE, savedRecord.getHealthCode());
            assertEquals(RECORD_ID, savedRecord.getId());
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

        } finally {
            mapper.delete(testRecord);
        }
    }
}
