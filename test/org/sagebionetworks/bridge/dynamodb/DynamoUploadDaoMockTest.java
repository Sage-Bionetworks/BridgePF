package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.dynamodb.DynamoUploadDaoTest.createUploadRequest;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadRequest;

public class DynamoUploadDaoMockTest {
    @Test
    public void createUpload() {
        // mock DDB mapper (new)
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);

        // execute
        DynamoUploadDao dao = new DynamoUploadDao();
        dao.setDdbMapper(mockMapper);
        UploadRequest req = createUploadRequest();
        String uploadId = dao.createUpload(req, "fakeHealthCode");

        // Validate that our mock DDB mapper was called.
        ArgumentCaptor<DynamoUpload2> arg = ArgumentCaptor.forClass(DynamoUpload2.class);
        verify(mockMapper).save(arg.capture());

        // Validate that our DDB upload object matches our upload request, and that the upload ID matches.
        assertEquals(uploadId, arg.getValue().getUploadId());
        assertEquals(req.getContentLength(), arg.getValue().getContentLength());
        assertEquals(req.getContentMd5(), arg.getValue().getContentMd5());
        assertEquals(req.getContentType(), arg.getValue().getContentType());
        assertEquals(req.getName(), arg.getValue().getFilename());
    }

    @Test
    public void getUploadFromNew() {
        // mock DDB mapper (new)
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
    public void getUploadFromOld() {
        // mock DDB mapper (new)
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        ArgumentCaptor<DynamoUpload2> arg = ArgumentCaptor.forClass(DynamoUpload2.class);
        when(mockMapper.load(arg.capture())).thenReturn(null);

        // mock DDB mapper (old)
        DynamoUpload uploadOld = new DynamoUpload();
        DynamoDBMapper mockMapperOld = mock(DynamoDBMapper.class);
        ArgumentCaptor<DynamoUpload> argOld = ArgumentCaptor.forClass(DynamoUpload.class);
        when(mockMapperOld.load(argOld.capture())).thenReturn(uploadOld);

        // execute
        DynamoUploadDao dao = new DynamoUploadDao();
        dao.setDdbMapper(mockMapper);
        dao.setDdbMapperOld(mockMapperOld);
        Upload retVal = dao.getUpload("test-get-fallback");
        assertSame(uploadOld, retVal);

        // validate we passed in the expected key
        assertEquals("test-get-fallback", arg.getValue().getUploadId());
        assertEquals("test-get-fallback", argOld.getValue().getUploadId());
    }

    @Test
    public void getUploadNotFound() {
        // mock DDB mapper (new)
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        ArgumentCaptor<DynamoUpload2> arg = ArgumentCaptor.forClass(DynamoUpload2.class);
        when(mockMapper.load(arg.capture())).thenReturn(null);

        // mock DDB mapper (old)
        DynamoDBMapper mockMapperOld = mock(DynamoDBMapper.class);
        ArgumentCaptor<DynamoUpload> argOld = ArgumentCaptor.forClass(DynamoUpload.class);
        when(mockMapperOld.load(argOld.capture())).thenReturn(null);

        // execute
        DynamoUploadDao dao = new DynamoUploadDao();
        dao.setDdbMapper(mockMapper);
        dao.setDdbMapperOld(mockMapperOld);

        Exception thrown = null;
        try {
            dao.getUpload("test-get-404");
            fail();
        } catch (BridgeServiceException ex) {
            thrown = ex;
        }
        assertNotNull(thrown);

        // validate we passed in the expected key
        assertEquals("test-get-404", arg.getValue().getUploadId());
        assertEquals("test-get-404", argOld.getValue().getUploadId());
    }

    @Test
    public void uploadCompleteNew() {
        // mock DDB mapper (new)
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        ArgumentCaptor<DynamoUpload2> argLoad = ArgumentCaptor.forClass(DynamoUpload2.class);
        when(mockMapper.load(argLoad.capture())).thenReturn(new DynamoUpload2());

        // execute
        DynamoUploadDao dao = new DynamoUploadDao();
        dao.setDdbMapper(mockMapper);
        dao.uploadComplete("test-upload-complete");

        // validate we passed in the expected key
        assertEquals("test-upload-complete", argLoad.getValue().getUploadId());

        // Verify our mock. We add complete=true and uploadDate on save, so only check for those properties.
        ArgumentCaptor<DynamoUpload2> argSave = ArgumentCaptor.forClass(DynamoUpload2.class);
        verify(mockMapper).save(argSave.capture());
        assertTrue(argSave.getValue().isComplete());

        // There is a slim chance that this will fail if it runs just after midnight.
        assertEquals(LocalDate.now(DateTimeZone.forID("America/Los_Angeles")), argSave.getValue().getUploadDate());
    }

    @Test
    public void uploadCompleteOld() {
        // mock DDB mapper (new)
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        ArgumentCaptor<DynamoUpload2> argLoad = ArgumentCaptor.forClass(DynamoUpload2.class);
        when(mockMapper.load(argLoad.capture())).thenReturn(null);

        // mock DDB mapper (old)
        DynamoDBMapper mockMapperOld = mock(DynamoDBMapper.class);
        ArgumentCaptor<DynamoUpload> argLoadOld = ArgumentCaptor.forClass(DynamoUpload.class);
        when(mockMapperOld.load(argLoadOld.capture())).thenReturn(new DynamoUpload());

        // execute
        DynamoUploadDao dao = new DynamoUploadDao();
        dao.setDdbMapper(mockMapper);
        dao.setDdbMapperOld(mockMapperOld);
        dao.uploadComplete("test-complete-fallback");

        // validate we passed in the expected key
        assertEquals("test-complete-fallback", argLoad.getValue().getUploadId());
        assertEquals("test-complete-fallback", argLoadOld.getValue().getUploadId());

        // Verify our mock. We add complete=true only for the old code path.
        ArgumentCaptor<DynamoUpload> argSave = ArgumentCaptor.forClass(DynamoUpload.class);
        verify(mockMapperOld).save(argSave.capture());
        assertTrue(argSave.getValue().isComplete());
    }

    @Test
    public void uploadCompleteNotFound() {
        // mock DDB mapper (new)
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        ArgumentCaptor<DynamoUpload2> argLoad = ArgumentCaptor.forClass(DynamoUpload2.class);
        when(mockMapper.load(argLoad.capture())).thenReturn(null);

        // mock DDB mapper (old)
        DynamoDBMapper mockMapperOld = mock(DynamoDBMapper.class);
        ArgumentCaptor<DynamoUpload> argLoadOld = ArgumentCaptor.forClass(DynamoUpload.class);
        when(mockMapperOld.load(argLoadOld.capture())).thenReturn(null);

        // execute
        DynamoUploadDao dao = new DynamoUploadDao();
        dao.setDdbMapper(mockMapper);
        dao.setDdbMapperOld(mockMapperOld);

        Exception thrown = null;
        try {
            dao.uploadComplete("test-complete-404");
            fail();
        } catch (BridgeServiceException ex) {
            thrown = ex;
        }
        assertNotNull(thrown);

        // validate we passed in the expected key
        assertEquals("test-complete-404", argLoad.getValue().getUploadId());
        assertEquals("test-complete-404", argLoadOld.getValue().getUploadId());

        // mapper.save() is never called in this branch
    }
}
