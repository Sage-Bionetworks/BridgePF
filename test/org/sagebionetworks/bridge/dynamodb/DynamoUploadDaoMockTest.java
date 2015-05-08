package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.exceptions.NotFoundException;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;

public class DynamoUploadDaoMockTest {
    @Test
    public void createUpload() {
        // mock DDB mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);

        // execute
        DynamoUploadDao dao = new DynamoUploadDao();
        dao.setDdbMapper(mockMapper);
        UploadRequest req = createUploadRequest();
        Upload upload = dao.createUpload(req, "fakeHealthCode");

        // Validate that our mock DDB mapper was called.
        ArgumentCaptor<DynamoUpload2> arg = ArgumentCaptor.forClass(DynamoUpload2.class);
        verify(mockMapper).save(arg.capture());

        // Validate that our DDB upload object matches our upload request, and that the upload ID matches.
        assertEquals(upload.getUploadId(), arg.getValue().getUploadId());
        assertEquals(req.getContentLength(), arg.getValue().getContentLength());
        assertEquals(req.getContentMd5(), arg.getValue().getContentMd5());
        assertEquals(req.getContentType(), arg.getValue().getContentType());
        assertEquals(req.getName(), arg.getValue().getFilename());
    }

    @Test
    public void getUpload() {
        // mock DDB mapper
        DynamoUpload2 upload = new DynamoUpload2();
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        ArgumentCaptor<DynamoUpload2> arg = ArgumentCaptor.forClass(DynamoUpload2.class);
        when(mockMapper.load(arg.capture())).thenReturn(upload);

        // execute
        DynamoUploadDao dao = new DynamoUploadDao();
        dao.setDdbMapper(mockMapper);
        Upload retVal = dao.getUpload("test-get-upload");
        assertSame(upload, retVal);

        // validate we passed in the expected key
        assertEquals("test-get-upload", arg.getValue().getUploadId());
    }

    @Test
    public void getUploadNotFound() {
        // mock DDB mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        ArgumentCaptor<DynamoUpload2> arg = ArgumentCaptor.forClass(DynamoUpload2.class);
        when(mockMapper.load(arg.capture())).thenReturn(null);

        // execute
        DynamoUploadDao dao = new DynamoUploadDao();
        dao.setDdbMapper(mockMapper);

        Exception thrown = null;
        try {
            dao.getUpload("test-get-404");
            fail();
        } catch (NotFoundException ex) {
            thrown = ex;
        }
        assertNotNull(thrown);

        // validate we passed in the expected key
        assertEquals("test-get-404", arg.getValue().getUploadId());
    }

    @Test
    public void uploadComplete() {
        // mock DDB mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);

        // execute
        DynamoUploadDao dao = new DynamoUploadDao();
        dao.setDdbMapper(mockMapper);
        dao.uploadComplete(new DynamoUpload2());

        // Verify our mock. We add status=VALIDATION_IN_PROGRESS and uploadDate on save, so only check for those
        // properties.
        ArgumentCaptor<DynamoUpload2> argSave = ArgumentCaptor.forClass(DynamoUpload2.class);
        verify(mockMapper).save(argSave.capture());
        assertEquals(UploadStatus.VALIDATION_IN_PROGRESS, argSave.getValue().getStatus());

        // There is a slim chance that this will fail if it runs just after midnight.
        assertEquals(LocalDate.now(DateTimeZone.forID("America/Los_Angeles")), argSave.getValue().getUploadDate());
    }

    @Test
    public void writeValidationStatus() {
        // create input
        DynamoUpload2 upload2 = new DynamoUpload2();
        upload2.setUploadId("test-upload");
        upload2.setValidationMessageList(Collections.<String>emptyList());

        // mock DDB mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);

        // execute
        DynamoUploadDao dao = new DynamoUploadDao();
        dao.setDdbMapper(mockMapper);
        dao.writeValidationStatus(upload2, UploadStatus.SUCCEEDED, ImmutableList.of("wrote new"));

        // Verify our mock. We set the status and append messages.
        ArgumentCaptor<DynamoUpload2> argSave = ArgumentCaptor.forClass(DynamoUpload2.class);
        verify(mockMapper).save(argSave.capture());
        assertEquals(UploadStatus.SUCCEEDED, argSave.getValue().getStatus());

        List<String> messageList = argSave.getValue().getValidationMessageList();
        assertEquals(1, messageList.size());
        assertEquals("wrote new", messageList.get(0));
    }

    @Test
    public void writeValidationStatusNonEmptyList() {
        // create input
        DynamoUpload2 upload2 = new DynamoUpload2();
        upload2.setUploadId("test-upload");
        upload2.setValidationMessageList(ImmutableList.of("pre-existing message"));

        // mock DDB mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);

        // execute
        DynamoUploadDao dao = new DynamoUploadDao();
        dao.setDdbMapper(mockMapper);
        dao.writeValidationStatus(upload2, UploadStatus.SUCCEEDED, ImmutableList.of("appended this message"));

        // Verify our mock. We set the status and append messages.
        ArgumentCaptor<DynamoUpload2> argSave = ArgumentCaptor.forClass(DynamoUpload2.class);
        verify(mockMapper).save(argSave.capture());
        assertEquals(UploadStatus.SUCCEEDED, argSave.getValue().getStatus());

        List<String> messageList = argSave.getValue().getValidationMessageList();
        assertEquals(2, messageList.size());
        assertEquals("pre-existing message", messageList.get(0));
        assertEquals("appended this message", messageList.get(1));
    }

    private static UploadRequest createUploadRequest() {
        final String text = "test upload dao";
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("name", "test-upload-dao-filename");
        node.put("contentType", "text/plain");
        node.put("contentLength", text.getBytes().length);
        node.put("contentMd5", Base64.encodeBase64String(DigestUtils.md5(text)));
        return UploadRequest.fromJson(node);
    }
}
