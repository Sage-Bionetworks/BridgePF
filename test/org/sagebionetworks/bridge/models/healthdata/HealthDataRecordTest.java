package org.sagebionetworks.bridge.models.healthdata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.databind.node.IntNode;
import com.google.common.collect.ImmutableMap;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;
import org.springframework.validation.MapBindingResult;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.validators.HealthDataRecordValidator;
import org.sagebionetworks.bridge.validators.Validate;

@SuppressWarnings("unchecked")
public class HealthDataRecordTest {
    private static final String APP_VERSION = "version 1.0.0, build 2";
    private static final long CREATED_ON_MILLIS = 1502498010000L;
    private static final JsonNode DUMMY_DATA = BridgeObjectMapper.get().createObjectNode();
    private static final JsonNode DUMMY_METADATA = BridgeObjectMapper.get().createObjectNode();
    private static final JsonNode DUMMY_USER_METADATA = BridgeObjectMapper.get().createObjectNode();
    private static final String PHONE_INFO = "Unit Tests";
    private static final LocalDate UPLOAD_DATE = LocalDate.parse("2017-08-11");

    @Test
    public void normalCase() {
        // build
        HealthDataRecord record = makeValidRecord();

        // validate
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);

        assertEquals(CREATED_ON_MILLIS, record.getCreatedOn().longValue());
        assertSame(DUMMY_DATA, record.getData());
        assertEquals("dummy healthcode", record.getHealthCode());
        assertNull(record.getId());
        assertSame(DUMMY_METADATA, record.getMetadata());
        assertEquals("dummy schema", record.getSchemaId());
        assertEquals(3, record.getSchemaRevision());
        assertEquals("dummy study", record.getStudyId());
        assertEquals(UPLOAD_DATE, record.getUploadDate());
        assertEquals(TestConstants.USER_DATA_GROUPS, record.getUserDataGroups());
        assertEquals(SharingScope.NO_SHARING, record.getUserSharingScope());
    }

    @Test
    public void optionalValues() {
        // optional values
        long uploadedOn = 1462575525894L;

        // build
        HealthDataRecord record = makeValidRecord();
        record.setId("optional record ID");
        record.setAppVersion(APP_VERSION);
        record.setCreatedOnTimeZone("+0900");
        record.setPhoneInfo(PHONE_INFO);
        record.setRawDataAttachmentId("raw.zip");
        record.setSynapseExporterStatus(HealthDataRecord.ExporterStatus.NOT_EXPORTED);
        record.setUploadId("optional upload ID");
        record.setUploadedOn(uploadedOn);
        record.setUserExternalId("optional external ID");
        record.setUserMetadata(DUMMY_USER_METADATA);
        record.setValidationErrors("dummy validation errors");
        record.setVersion(42L);

        // validate
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);

        assertEquals("optional record ID", record.getId());
        assertEquals(APP_VERSION, record.getAppVersion());
        assertEquals("+0900", record.getCreatedOnTimeZone());
        assertEquals(PHONE_INFO, record.getPhoneInfo());
        assertEquals("raw.zip", record.getRawDataAttachmentId());
        assertEquals(HealthDataRecord.ExporterStatus.NOT_EXPORTED, record.getSynapseExporterStatus());
        assertEquals("optional upload ID", record.getUploadId());
        assertEquals(uploadedOn, record.getUploadedOn().longValue());
        assertEquals("optional external ID", record.getUserExternalId());
        assertSame(DUMMY_USER_METADATA, record.getUserMetadata());
        assertEquals("dummy validation errors", record.getValidationErrors());
        assertEquals(42, record.getVersion().longValue());
    }
    
    @Test
    public void emptyDataGroupSetConvertedToNull() {
        HealthDataRecord record = makeValidRecord();
        record.setUserDataGroups(new HashSet<>());
        assertNull(record.getUserDataGroups());
    }
    
    @Test
    public void emptySubstudyMembershipsConvertedToNull() {
        HealthDataRecord record = makeValidRecord();
        record.setUserSubstudyMemberships(ImmutableMap.of());
        assertNull(record.getUserSubstudyMemberships());
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonNullData() throws Exception {
        JsonNode data = BridgeObjectMapper.get().readTree("null");
        HealthDataRecord record = makeValidRecord();
        record.setData(data);
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test(expected = InvalidEntityException.class)
    public void dataIsNotMap() throws Exception {
        JsonNode data = BridgeObjectMapper.get().readTree("\"This is not a map.\"");
        HealthDataRecord record = makeValidRecord();
        record.setData(data);
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test(expected = InvalidEntityException.class)
    public void validatorWithNullData() {
        HealthDataRecord record = makeValidRecord();
        record.setData(null);
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test(expected = InvalidEntityException.class)
    public void nullHealthCode() {
        HealthDataRecord record = makeValidRecord();
        record.setHealthCode(null);
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test(expected = InvalidEntityException.class)
    public void emptyHealthCode() {
        HealthDataRecord record = makeValidRecord();
        record.setHealthCode("");
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test(expected = InvalidEntityException.class)
    public void emptyId() {
        HealthDataRecord record = makeValidRecord();
        record.setId("");
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test(expected = InvalidEntityException.class)
    public void validatorWithNullCreatedOn() {
        HealthDataRecord record = makeValidRecord();
        record.setCreatedOn(null);
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonNullMetadata() throws Exception {
        JsonNode metadata = BridgeObjectMapper.get().readTree("null");
        HealthDataRecord record = makeValidRecord();
        record.setMetadata(metadata);
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test(expected = InvalidEntityException.class)
    public void metadataIsNotMap() throws Exception {
        JsonNode metadata = BridgeObjectMapper.get().readTree("\"This is not a map.\"");
        HealthDataRecord record = makeValidRecord();
        record.setMetadata(metadata);
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test(expected = InvalidEntityException.class)
    public void validatorWithNullMetadata() {
        HealthDataRecord record = makeValidRecord();
        record.setMetadata(null);
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test(expected = InvalidEntityException.class)
    public void nullSchemaId() {
        HealthDataRecord record = makeValidRecord();
        record.setSchemaId(null);
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test(expected = InvalidEntityException.class)
    public void emptySchemaId() {
        HealthDataRecord record = makeValidRecord();
        record.setSchemaId("");
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test(expected = InvalidEntityException.class)
    public void validatorWithNullUploadDate() {
        HealthDataRecord record = makeValidRecord();
        record.setUploadDate(null);
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test
    public void validateUserMetadataNotAnObject() {
        HealthDataRecord record = makeValidRecord();
        record.setUserMetadata(IntNode.valueOf(42));
        assertValidatorMessage(HealthDataRecordValidator.INSTANCE, record, "userMetadata", "must be an object node");
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

    private static HealthDataRecord makeValidRecord() {
        HealthDataRecord record = HealthDataRecord.create();
        record.setCreatedOn(CREATED_ON_MILLIS);
        record.setData(DUMMY_DATA);
        record.setHealthCode("dummy healthcode");
        record.setMetadata(DUMMY_METADATA);
        record.setSchemaId("dummy schema");
        record.setSchemaRevision(3);
        record.setStudyId("dummy study");
        record.setUploadDate(UPLOAD_DATE);
        record.setUserDataGroups(TestConstants.USER_DATA_GROUPS);
        record.setUserSharingScope(SharingScope.NO_SHARING);
        return record;
    }

    @Test
    public void testSerialization() throws Exception {
        String uploadedOnStr = "2016-05-06T16:01:22.307-0700";
        long uploadedOn = DateTime.parse(uploadedOnStr).getMillis();

        // start with JSON
        String jsonText = "{\n" +
                "   \"appVersion\":\"" + APP_VERSION + "\",\n" +
                "   \"createdOn\":\"2014-02-12T13:45-0800\",\n" +
                "   \"createdOnTimeZone\":\"-0800\",\n" +
                "   \"data\":{\"myData\":\"myDataValue\"},\n" +
                "   \"healthCode\":\"json healthcode\",\n" +
                "   \"id\":\"json record ID\",\n" +
                "   \"metadata\":{\"myMetadata\":\"myMetaValue\"},\n" +
                "   \"phoneInfo\":\"" + PHONE_INFO + "\",\n" +
                "   \"rawDataAttachmentId\":\"raw.zip\",\n" +
                "   \"schemaId\":\"json schema\",\n" +
                "   \"schemaRevision\":3,\n" +
                "   \"studyId\":\"json study\",\n" +
                "   \"synapseExporterStatus\":\"not_exported\",\n" +
                "   \"uploadDate\":\"2014-02-12\",\n" +
                "   \"uploadId\":\"json upload\",\n" +
                "   \"uploadedOn\":\"" + uploadedOnStr + "\",\n" +
                "   \"userMetadata\":{\"userMetadata\":\"userMetaValue\"},\n" +
                "   \"userSharingScope\":\"all_qualified_researchers\",\n" +
                "   \"userExternalId\":\"ABC-123-XYZ\",\n" +
                "   \"validationErrors\":\"dummy validation errors\",\n" +
                "   \"version\":42\n" +
                "}";
        long measuredTimeMillis = new DateTime(2014, 2, 12, 13, 45, BridgeConstants.LOCAL_TIME_ZONE).getMillis();

        // convert to POJO
        HealthDataRecord record = BridgeObjectMapper.get().readValue(jsonText, HealthDataRecord.class);
        assertEquals(APP_VERSION, record.getAppVersion());
        assertEquals(measuredTimeMillis, record.getCreatedOn().longValue());
        assertEquals("-0800", record.getCreatedOnTimeZone());
        assertEquals("json healthcode", record.getHealthCode());
        assertEquals("json record ID", record.getId());
        assertEquals(PHONE_INFO, record.getPhoneInfo());
        assertEquals("raw.zip", record.getRawDataAttachmentId());
        assertEquals("json schema", record.getSchemaId());
        assertEquals(3, record.getSchemaRevision());
        assertEquals("json study", record.getStudyId());
        assertEquals(HealthDataRecord.ExporterStatus.NOT_EXPORTED, record.getSynapseExporterStatus());
        assertEquals("2014-02-12", record.getUploadDate().toString(ISODateTimeFormat.date()));
        assertEquals("json upload", record.getUploadId());
        assertEquals(uploadedOn, record.getUploadedOn().longValue());
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, record.getUserSharingScope());
        assertEquals("ABC-123-XYZ", record.getUserExternalId());
        assertEquals("dummy validation errors", record.getValidationErrors());
        assertEquals(42, record.getVersion().longValue());

        assertEquals(1, record.getData().size());
        assertEquals("myDataValue", record.getData().get("myData").textValue());

        assertEquals(1, record.getMetadata().size());
        assertEquals("myMetaValue", record.getMetadata().get("myMetadata").textValue());

        assertEquals(1, record.getUserMetadata().size());
        assertEquals("userMetaValue", record.getUserMetadata().get("userMetadata").textValue());

        // convert back to JSON
        String convertedJson = BridgeObjectMapper.get().writeValueAsString(record);

        // then convert to a map so we can validate the raw JSON
        Map<String, Object> jsonMap = BridgeObjectMapper.get().readValue(convertedJson, JsonUtils.TYPE_REF_RAW_MAP);
        assertEquals(22, jsonMap.size());
        assertEquals(APP_VERSION, jsonMap.get("appVersion"));
        assertEquals("-0800", jsonMap.get("createdOnTimeZone"));
        assertEquals("json healthcode", jsonMap.get("healthCode"));
        assertEquals("json record ID", jsonMap.get("id"));
        assertEquals(PHONE_INFO, jsonMap.get("phoneInfo"));
        assertEquals("raw.zip", jsonMap.get("rawDataAttachmentId"));
        assertEquals("json schema", jsonMap.get("schemaId"));
        assertEquals(3, jsonMap.get("schemaRevision"));
        assertEquals("json study", jsonMap.get("studyId"));
        assertEquals("not_exported", jsonMap.get("synapseExporterStatus"));
        assertEquals("2014-02-12", jsonMap.get("uploadDate"));
        assertEquals("json upload", jsonMap.get("uploadId"));
        assertEquals(uploadedOn, DateTime.parse((String) jsonMap.get("uploadedOn")).getMillis());
        assertEquals("all_qualified_researchers", jsonMap.get("userSharingScope"));
        assertEquals("ABC-123-XYZ", jsonMap.get("userExternalId"));
        assertEquals("dummy validation errors", jsonMap.get("validationErrors"));
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

        Map<String, String> userMetadata = (Map<String, String>) jsonMap.get("userMetadata");
        assertEquals(1, userMetadata.size());
        assertEquals("userMetaValue", userMetadata.get("userMetadata"));

        // convert back to JSON with PUBLIC_RECORD_WRITER
        String publicJson = HealthDataRecord.PUBLIC_RECORD_WRITER.writeValueAsString(record);

        // Convert back to map again. Only validate a few key fields are present and the filtered fields are absent.
        Map<String, Object> publicJsonMap = BridgeObjectMapper.get().readValue(publicJson, JsonUtils.TYPE_REF_RAW_MAP);
        assertEquals(21, publicJsonMap.size());
        assertFalse(publicJsonMap.containsKey("healthCode"));
        assertEquals("json record ID", publicJsonMap.get("id"));
    }

    @Test
    public void testTimeZoneFormatter() {
        testTimeZoneFormatter("+0000", DateTimeZone.UTC);
        testTimeZoneFormatter("+0000", DateTimeZone.forOffsetHours(0));
        testTimeZoneFormatter("+0900", DateTimeZone.forOffsetHours(+9));
        testTimeZoneFormatter("-0800", DateTimeZone.forOffsetHours(-8));
        testTimeZoneFormatter("+1345", DateTimeZone.forOffsetHoursMinutes(+13, +45));
        testTimeZoneFormatter("-0330", DateTimeZone.forOffsetHoursMinutes(-3, -30));

        testTimeZoneFormatterForString("+0000", "Z");
        testTimeZoneFormatterForString("+0000", "+00:00");
        testTimeZoneFormatterForString("+0900", "+09:00");
        testTimeZoneFormatterForString("-0800", "-08:00");
        testTimeZoneFormatterForString("+1345", "+13:45");
        testTimeZoneFormatterForString("-0330", "-03:30");
    }

    private static void testTimeZoneFormatter(String expected, DateTimeZone timeZone) {
        // The formatter only takes in DateTimes, not TimeZones. To test this, create a dummy DateTime with the given
        // TimeZone
        DateTime dateTime = new DateTime(2017, 1, 25, 2, 29, timeZone);
        assertEquals(expected, HealthDataRecord.TIME_ZONE_FORMATTER.print(dateTime));
    }

    private static void testTimeZoneFormatterForString(String expected, String timeZoneStr) {
        // DateTimeZone doesn't have an API to parse an ISO timezone representation, so we have to parse an entire date
        // just to parse the timezone.
        DateTime dateTime = DateTime.parse("2017-01-25T02:29" + timeZoneStr);
        assertEquals(expected, HealthDataRecord.TIME_ZONE_FORMATTER.print(dateTime));
    }
}
