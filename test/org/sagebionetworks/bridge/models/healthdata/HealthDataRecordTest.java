package org.sagebionetworks.bridge.models.healthdata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;
import org.springframework.validation.MapBindingResult;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.HealthDataDao;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataDao;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.validators.HealthDataRecordValidator;

@SuppressWarnings("unchecked")
public class HealthDataRecordTest {
    // We want to do as much testing as possible through the generic interface, so we have this DAO that we use just
    // for getRecordBuilder().
    private static final HealthDataDao DAO = new DynamoHealthDataDao();
    
    @Test
    public void testBuilder() {
        // build
        HealthDataRecord record = DAO.getRecordBuilder().withHealthCode("dummy healthcode")
                .withSchemaId("dummy schema").withSchemaRevision(3).withStudyId("dummy study")
                .withUserDataGroups(TestConstants.USER_DATA_GROUPS).build();

        // validate
        assertEquals("dummy healthcode", record.getHealthCode());
        assertNull(record.getId());
        assertEquals("dummy schema", record.getSchemaId());
        assertEquals(3, record.getSchemaRevision());
        assertEquals("dummy study", record.getStudyId());
        assertEquals(TestConstants.USER_DATA_GROUPS, record.getUserDataGroups());

        assertTrue(record.getData().isObject());
        assertEquals(0, record.getData().size());

        assertTrue(record.getMetadata().isObject());
        assertEquals(0, record.getMetadata().size());

        // for default date and time, just check they're not null, so we don't get weird clock skew errors
        assertNotNull(record.getCreatedOn());
        assertNotNull(record.getUploadDate());
    }

    @Test
    public void optionalValues() throws Exception {
        // optional values
        JsonNode data = BridgeObjectMapper.get().readTree("{\"myData\":\"myDataValue\"}");
        JsonNode metadata = BridgeObjectMapper.get().readTree("{\"myMetadata\":\"myMetaValue\"}");
        long arbitraryTimestamp = 1424136378727L;
        long uploadedOn = 1462575525894L;

        // arbitrarily 2015-02-12
        LocalDate uploadDate = new LocalDate(2014, 2, 12);

        // build
        HealthDataRecord record = DAO.getRecordBuilder().withData(data).withHealthCode("required healthcode")
                .withId("optional record ID").withCreatedOn(arbitraryTimestamp).withMetadata(metadata)
                .withSchemaId("required schema").withSchemaRevision(3).withStudyId("required study")
                .withUploadDate(uploadDate).withUploadId("optional upload ID").withUploadedOn(uploadedOn)
                .withUserExternalId("optional external ID")
                .withUserSharingScope(ParticipantOption.SharingScope.SPONSORS_AND_PARTNERS)
                .withUserDataGroups(TestConstants.USER_DATA_GROUPS)
                .withVersion(42L).build();

        // validate
        assertEquals("required healthcode", record.getHealthCode());
        assertEquals("optional record ID", record.getId());
        assertEquals(arbitraryTimestamp, record.getCreatedOn().longValue());
        assertEquals("required schema", record.getSchemaId());
        assertEquals(3, record.getSchemaRevision());
        assertEquals("required study", record.getStudyId());
        assertEquals("2014-02-12", record.getUploadDate().toString(ISODateTimeFormat.date()));
        assertEquals("optional upload ID", record.getUploadId());
        assertEquals(uploadedOn, record.getUploadedOn().longValue());
        assertEquals("optional external ID", record.getUserExternalId());
        assertEquals(ParticipantOption.SharingScope.SPONSORS_AND_PARTNERS, record.getUserSharingScope());
        assertEquals(TestConstants.USER_DATA_GROUPS, record.getUserDataGroups());
        assertEquals(42, record.getVersion().longValue());

        assertEquals(1, record.getData().size());
        assertEquals("myDataValue", record.getData().get("myData").asText());

        assertEquals(1, record.getMetadata().size());
        assertEquals("myMetaValue", record.getMetadata().get("myMetadata").asText());

        // test copy constructor
        HealthDataRecord copyRecord = DAO.getRecordBuilder().copyOf(record).build();
        assertEquals("required healthcode", copyRecord.getHealthCode());
        assertEquals("optional record ID", copyRecord.getId());
        assertEquals(arbitraryTimestamp, copyRecord.getCreatedOn().longValue());
        assertEquals("required schema", copyRecord.getSchemaId());
        assertEquals(3, copyRecord.getSchemaRevision());
        assertEquals("required study", copyRecord.getStudyId());
        assertEquals("2014-02-12", copyRecord.getUploadDate().toString(ISODateTimeFormat.date()));
        assertEquals("optional upload ID", copyRecord.getUploadId());
        assertEquals(uploadedOn, copyRecord.getUploadedOn().longValue());
        assertEquals("optional external ID", copyRecord.getUserExternalId());
        assertEquals(ParticipantOption.SharingScope.SPONSORS_AND_PARTNERS, copyRecord.getUserSharingScope());
        assertEquals(TestConstants.USER_DATA_GROUPS, record.getUserDataGroups());
        assertEquals(42, copyRecord.getVersion().longValue());

        assertEquals(1, copyRecord.getData().size());
        assertEquals("myDataValue", copyRecord.getData().get("myData").asText());

        assertEquals(1, copyRecord.getMetadata().size());
        assertEquals("myMetaValue", copyRecord.getMetadata().get("myMetadata").asText());
    }
    
    @Test
    public void emptyDataGroupSetConvertedToNull() {
        HealthDataRecord record = DAO.getRecordBuilder().withUserDataGroups(new HashSet<>()).buildUnvalidated();
        
        assertNull(record.getUserDataGroups());
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonNullData() throws Exception {
        JsonNode data = BridgeObjectMapper.get().readTree("null");
        DAO.getRecordBuilder().withData(data).withHealthCode("valid healthcode").withSchemaId("valid schema")
                .withSchemaRevision(3).withStudyId("valid study").build();
    }

    @Test(expected = InvalidEntityException.class)
    public void dataIsNotMap() throws Exception {
        JsonNode data = BridgeObjectMapper.get().readTree("\"This is not a map.\"");
        DAO.getRecordBuilder().withData(data).withHealthCode("valid healthcode").withSchemaId("valid schema")
                .withSchemaRevision(3).withStudyId("valid study").build();
    }

    // branch coverage
    // We create a DynamoHealthDataRecord directly, since the builder fills in defaults
    @Test
    public void validatorWithNullData() {
        // build and overwrite data
        DynamoHealthDataRecord record = (DynamoHealthDataRecord) DAO.getRecordBuilder()
                .withHealthCode("valid healthcode").withSchemaId("valid schema").withSchemaRevision(3)
                .withStudyId("valid study").build();
        record.setData(null);

        // validate
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "HealthDataRecord");
        HealthDataRecordValidator.INSTANCE.validate(record, errors);
        assertTrue(errors.hasErrors());
    }

    @Test(expected = InvalidEntityException.class)
    public void nullHealthCode() {
        DAO.getRecordBuilder().withHealthCode(null).withSchemaId("valid schema").withSchemaRevision(3)
                .withStudyId("valid study").build();
    }

    @Test(expected = InvalidEntityException.class)
    public void emptyHealthCode() {
        DAO.getRecordBuilder().withHealthCode("").withSchemaId("valid schema").withSchemaRevision(3)
                .withStudyId("valid study").build();
    }

    @Test(expected = InvalidEntityException.class)
    public void emptyId() {
        DAO.getRecordBuilder().withHealthCode("valid healthcode").withId("").withSchemaId("valid schema")
                .withSchemaRevision(3).withStudyId("valid study").build();
    }

    // branch coverage
    // We create a DynamoHealthDataRecord directly, since the builder fills in defaults
    @Test
    public void validatorWithNullMeasuredTime() {
        // build and overwrite measuredTime
        DynamoHealthDataRecord record = (DynamoHealthDataRecord) DAO.getRecordBuilder()
                .withHealthCode("valid healthcode").withSchemaId("valid schema").withSchemaRevision(3)
                .withStudyId("valid study").build();
        record.setCreatedOn(null);

        // validate
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "HealthDataRecord");
        HealthDataRecordValidator.INSTANCE.validate(record, errors);
        assertTrue(errors.hasErrors());
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonNullMetadata() throws Exception {
        JsonNode metadata = BridgeObjectMapper.get().readTree("null");
        DAO.getRecordBuilder().withMetadata(metadata).withHealthCode("valid healthcode").withSchemaId("valid schema")
                .withSchemaRevision(3).withStudyId("valid study").build();
    }

    @Test(expected = InvalidEntityException.class)
    public void metadataIsNotMap() throws Exception {
        JsonNode metadata = BridgeObjectMapper.get().readTree("\"This is not a map.\"");
        DAO.getRecordBuilder().withMetadata(metadata).withHealthCode("valid healthcode").withSchemaId("valid schema")
                .withSchemaRevision(3).withStudyId("valid study").build();
    }

    // branch coverage
    // We create a DynamoHealthDataRecord directly, since the builder fills in defaults
    @Test
    public void validatorWithNullMetadata() {
        // build and overwrite metadata
        DynamoHealthDataRecord record = (DynamoHealthDataRecord) DAO.getRecordBuilder()
                .withHealthCode("valid healthcode").withSchemaId("valid schema").withSchemaRevision(3)
                .withStudyId("valid study").build();
        record.setMetadata(null);

        // validate
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "HealthDataRecord");
        HealthDataRecordValidator.INSTANCE.validate(record, errors);
        assertTrue(errors.hasErrors());
    }

    @Test(expected = InvalidEntityException.class)
    public void nullSchemaId() {
        DAO.getRecordBuilder().withHealthCode("valid healthcode").withSchemaId(null).withSchemaRevision(3)
                .withStudyId("valid study").build();
    }

    @Test(expected = InvalidEntityException.class)
    public void emptySchemaId() {
        DAO.getRecordBuilder().withHealthCode("valid healthcode").withSchemaId("").withSchemaRevision(3)
                .withStudyId("valid study").build();
    }

    // branch coverage
    // We create a DynamoHealthDataRecord directly, since the builder fills in defaults
    @Test
    public void validatorWithNullUploadDate() {
        // build and overwrite metadata
        DynamoHealthDataRecord record = (DynamoHealthDataRecord) DAO.getRecordBuilder()
                .withHealthCode("valid healthcode").withSchemaId("valid schema").withSchemaRevision(3)
                .withStudyId("valid study").build();
        record.setUploadDate(null);

        // validate
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "HealthDataRecord");
        HealthDataRecordValidator.INSTANCE.validate(record, errors);
        assertTrue(errors.hasErrors());
    }

    // branch coverage
    @Test
    public void validatorSupportsClass() {
        assertTrue(HealthDataRecordValidator.INSTANCE.supports(HealthDataRecord.class));
    }

    // branch coverage
    @Test
    public void validatorSupportsSubclass() {
        assertTrue(HealthDataRecordValidator.INSTANCE.supports(DynamoHealthDataRecord.class));
    }

    // branch coverage
    @Test
    public void validatorDoesntSupportWrongClass() {
        assertFalse(HealthDataRecordValidator.INSTANCE.supports(String.class));
    }

    // branch coverage
    // we call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types
    @Test
    public void validateNull() {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "HealthDataRecord");
        HealthDataRecordValidator.INSTANCE.validate(null, errors);
        assertTrue(errors.hasErrors());
    }

    // branch coverage
    // we call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types
    @Test
    public void validateWrongClass() {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "HealthDataRecord");
        HealthDataRecordValidator.INSTANCE.validate("This is not a HealthDataRecord", errors);
        assertTrue(errors.hasErrors());
    }

    @Test
    public void testSerialization() throws Exception {
        String uploadedOnStr = "2016-05-06T16:01:22.307-0700";
        long uploadedOn = DateTime.parse(uploadedOnStr).getMillis();

        // start with JSON
        String jsonText = "{\n" +
                "   \"createdOn\":\"2014-02-12T13:45-0800\",\n" +
                "   \"data\":{\"myData\":\"myDataValue\"},\n" +
                "   \"healthCode\":\"json healthcode\",\n" +
                "   \"id\":\"json record ID\",\n" +
                "   \"metadata\":{\"myMetadata\":\"myMetaValue\"},\n" +
                "   \"schemaId\":\"json schema\",\n" +
                "   \"schemaRevision\":3,\n" +
                "   \"studyId\":\"json study\",\n" +
                "   \"uploadDate\":\"2014-02-12\",\n" +
                "   \"uploadId\":\"json upload\",\n" +
                "   \"uploadedOn\":\"" + uploadedOnStr + "\",\n" +
                "   \"userSharingScope\":\"all_qualified_researchers\",\n" +
                "   \"userExternalId\":\"ABC-123-XYZ\",\n" +
                "   \"version\":42\n" +
                "}";
        long measuredTimeMillis = new DateTime(2014, 2, 12, 13, 45, BridgeConstants.LOCAL_TIME_ZONE).getMillis();

        // convert to POJO
        HealthDataRecord record = BridgeObjectMapper.get().readValue(jsonText, HealthDataRecord.class);
        assertEquals(measuredTimeMillis, record.getCreatedOn().longValue());
        assertEquals("json healthcode", record.getHealthCode());
        assertEquals("json record ID", record.getId());
        assertEquals("json schema", record.getSchemaId());
        assertEquals(3, record.getSchemaRevision());
        assertEquals("json study", record.getStudyId());
        assertEquals("2014-02-12", record.getUploadDate().toString(ISODateTimeFormat.date()));
        assertEquals("json upload", record.getUploadId());
        assertEquals(uploadedOn, record.getUploadedOn().longValue());
        assertEquals(ParticipantOption.SharingScope.ALL_QUALIFIED_RESEARCHERS, record.getUserSharingScope());
        assertEquals("ABC-123-XYZ", record.getUserExternalId());
        assertEquals(42, record.getVersion().longValue());

        assertEquals(1, record.getData().size());
        assertEquals("myDataValue", record.getData().get("myData").asText());

        assertEquals(1, record.getMetadata().size());
        assertEquals("myMetaValue", record.getMetadata().get("myMetadata").asText());

        // convert back to JSON
        String convertedJson = BridgeObjectMapper.get().writeValueAsString(record);

        // then convert to a map so we can validate the raw JSON
        Map<String, Object> jsonMap = BridgeObjectMapper.get().readValue(convertedJson, JsonUtils.TYPE_REF_RAW_MAP);
        assertEquals(15, jsonMap.size());
        assertEquals("json healthcode", jsonMap.get("healthCode"));
        assertEquals("json record ID", jsonMap.get("id"));
        assertEquals("json schema", jsonMap.get("schemaId"));
        assertEquals(3, jsonMap.get("schemaRevision"));
        assertEquals("json study", jsonMap.get("studyId"));
        assertEquals("2014-02-12", jsonMap.get("uploadDate"));
        assertEquals("json upload", jsonMap.get("uploadId"));
        assertEquals(uploadedOn, DateTime.parse((String) jsonMap.get("uploadedOn")).getMillis());
        assertEquals("all_qualified_researchers", jsonMap.get("userSharingScope"));
        assertEquals("ABC-123-XYZ", jsonMap.get("userExternalId"));
        assertEquals(42, jsonMap.get("version"));
        assertEquals("HealthData", jsonMap.get("type"));

        DateTime convertedMeasuredTime = DateTime.parse((String) jsonMap.get("createdOn"));
        assertEquals(measuredTimeMillis, convertedMeasuredTime.getMillis());

        Map<String, String> data = (Map<String, String>) jsonMap.get("data");
        assertEquals(1, data.size());
        assertEquals("myDataValue", data.get("myData"));

        Map<String, String> metadata = (Map<String, String>) jsonMap.get("metadata");
        assertEquals(1, metadata.size());
        assertEquals("myMetaValue", metadata.get("myMetadata"));

        // convert back to JSON with PUBLIC_RECORD_WRITER
        String publicJson = HealthDataRecord.PUBLIC_RECORD_WRITER.writeValueAsString(record);

        // Convert back to map again. Only validate a few key fields are present and the filtered fields are absent.
        Map<String, Object> publicJsonMap = BridgeObjectMapper.get().readValue(publicJson, JsonUtils.TYPE_REF_RAW_MAP);
        assertEquals(14, publicJsonMap.size());
        assertFalse(publicJsonMap.containsKey("healthCode"));
        assertEquals("json record ID", publicJsonMap.get("id"));
    }
}
