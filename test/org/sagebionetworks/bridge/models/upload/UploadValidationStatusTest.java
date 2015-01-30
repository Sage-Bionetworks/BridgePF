package org.sagebionetworks.bridge.models.upload;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.validation.MapBindingResult;

import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.validators.UploadValidationStatusValidator;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class UploadValidationStatusTest {
    @Test
    public void builder() {
        UploadValidationStatus status = new UploadValidationStatus.Builder().withId("test-upload")
                .withMessageList(Collections.<String>emptyList()).withStatus(UploadStatus.SUCCEEDED).build();
        assertEquals("test-upload", status.getId());
        assertTrue(status.getMessageList().isEmpty());
        assertEquals(UploadStatus.SUCCEEDED, status.getStatus());
    }

    @Test
    public void withMessages() {
        UploadValidationStatus status = new UploadValidationStatus.Builder().withId("happy-case-2")
                .withMessageList(ImmutableList.of("foo", "bar", "baz")).withStatus(UploadStatus.VALIDATION_FAILED)
                .build();
        assertEquals("happy-case-2", status.getId());
        assertEquals(UploadStatus.VALIDATION_FAILED, status.getStatus());

        List<String> messageList = status.getMessageList();
        assertEquals(3, messageList.size());
        assertEquals("foo", messageList.get(0));
        assertEquals("bar", messageList.get(1));
        assertEquals("baz", messageList.get(2));
    }

    @Test
    public void fromUpload() {
        // make upload
        DynamoUpload2 upload2 = new DynamoUpload2();
        upload2.setUploadId("from-upload");
        upload2.appendValidationMessages(Collections.singletonList("foo"));
        upload2.setStatus(UploadStatus.SUCCEEDED);

        // construct and validate
        UploadValidationStatus status = UploadValidationStatus.from(upload2);
        assertEquals("from-upload", status.getId());
        assertEquals(UploadStatus.SUCCEEDED, status.getStatus());

        assertEquals(1, status.getMessageList().size());
        assertEquals("foo", status.getMessageList().get(0));
    }

    @Test(expected = InvalidEntityException.class)
    public void nullUpload() {
        UploadValidationStatus.from(null);
    }

    @Test(expected = InvalidEntityException.class)
    public void nullMessageList() {
        new UploadValidationStatus.Builder().withId("test-upload").withMessageList(null)
                .withStatus(UploadStatus.SUCCEEDED).build();
    }

    @Test(expected = InvalidEntityException.class)
    public void messageListWithNullString() {
        List<String> list = new ArrayList<>();
        list.add(null);

        new UploadValidationStatus.Builder().withId("test-upload").withMessageList(list)
                .withStatus(UploadStatus.SUCCEEDED).build();
    }

    @Test(expected = InvalidEntityException.class)
    public void messageListWithEmptyString() {
        List<String> list = new ArrayList<>();
        list.add("");

        new UploadValidationStatus.Builder().withId("test-upload").withMessageList(list)
                .withStatus(UploadStatus.SUCCEEDED).build();
    }

    @Test(expected = InvalidEntityException.class)
    public void nullId() {
        new UploadValidationStatus.Builder().withId(null).withMessageList(Collections.singletonList("foo"))
                .withStatus(UploadStatus.SUCCEEDED).build();
    }

    @Test(expected = InvalidEntityException.class)
    public void emptyId() {
        new UploadValidationStatus.Builder().withId("").withMessageList(Collections.singletonList("foo"))
                .withStatus(UploadStatus.SUCCEEDED).build();
    }

    @Test(expected = InvalidEntityException.class)
    public void nullStatus() {
        new UploadValidationStatus.Builder().withId("test-upload").withMessageList(Collections.singletonList("foo"))
                .withStatus(null).build();
    }

    // branch coverage
    @Test
    public void validatorSupportsClass() {
        assertTrue(UploadValidationStatusValidator.INSTANCE.supports(UploadValidationStatus.class));
    }

    // branch coverage
    @Test
    public void validatorDoesntSupportWrongClass() {
        assertFalse(UploadValidationStatusValidator.INSTANCE.supports(String.class));
    }

    // branch coverage
    // we call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types
    @Test
    public void validateNull() {
        MapBindingResult errors = new MapBindingResult(new HashMap(), "UploadValidationStatus");
        UploadValidationStatusValidator.INSTANCE.validate(null, errors);
        Assert.assertTrue(errors.hasErrors());
    }

    // branch coverage
    // we call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types
    @Test
    public void validateWrongClass() {
        MapBindingResult errors = new MapBindingResult(new HashMap(), "UploadValidationStatus");
        UploadValidationStatusValidator.INSTANCE.validate("this is the wrong class", errors);
        Assert.assertTrue(errors.hasErrors());
    }

    @Test
    public void serialization() throws Exception {
        // start with JSON
        String jsonText = "{\n" +
                "   \"id\":\"json-upload\",\n" +
                "   \"status\":\"SUCCEEDED\",\n" +
                "   \"messageList\":[\n" +
                "       \"foo\",\n" +
                "       \"bar\",\n" +
                "       \"baz\"\n" +
                "   ]\n" +
                "}";

        // convert to POJO
        UploadValidationStatus status = BridgeObjectMapper.get().readValue(jsonText, UploadValidationStatus.class);
        assertEquals("json-upload", status.getId());
        assertEquals(UploadStatus.SUCCEEDED, status.getStatus());

        List<String> messageList = status.getMessageList();
        assertEquals(3, messageList.size());
        assertEquals("foo", messageList.get(0));
        assertEquals("bar", messageList.get(1));
        assertEquals("baz", messageList.get(2));

        // convert back to JSON
        String convertedJson = BridgeObjectMapper.get().writeValueAsString(status);

        // then convert to a map so we can validate the raw JSON
        Map<String, Object> jsonMap = BridgeObjectMapper.get().readValue(convertedJson, JsonUtils.TYPE_REF_RAW_MAP);
        assertEquals(4, jsonMap.size());
        assertEquals("UploadValidationStatus", jsonMap.get("type"));
        assertEquals("json-upload", jsonMap.get("id"));
        assertEquals("SUCCEEDED", jsonMap.get("status"));

        List<String> messageJsonList = (List<String>) jsonMap.get("messageList");
        assertEquals(3, messageJsonList.size());
        assertEquals("foo", messageJsonList.get(0));
        assertEquals("bar", messageJsonList.get(1));
        assertEquals("baz", messageJsonList.get(2));
    }
}
