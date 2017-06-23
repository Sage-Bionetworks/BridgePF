package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.exceptions.NotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadCompletionClient;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.document.Index;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.internal.IteratorSupport;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class DynamoUploadDaoMockTest {
    
    private static String UPLOAD_ID = "uploadId";
    private static String UPLOAD_ID_2 = "uploadId2";
    
    @Mock
    private DynamoDBMapper mockMapper;
    
    @Mock
    private DynamoIndexHelper mockIndexHelper;
    
    @Mock
    private Index mockIndex;
    
    @Mock
    private ItemCollection<QueryOutcome> mockQueryOutcome;
    
    @Mock
    private QueryOutcome lastQueryOutcome;
    
    @Mock
    private QueryResult mockQueryResult;
    
    @Mock
    private IteratorSupport<Item,QueryOutcome> mockIterSupport;
    
    @Mock
    private DynamoUpload2 upload1;
    
    @Mock
    private DynamoUpload2 upload2;
    
    @Captor
    private ArgumentCaptor<QuerySpec> querySpecCaptor;
    
    @Captor
    private ArgumentCaptor<DynamoUpload2> uploadCaptor;
    
    @Captor
    private ArgumentCaptor<List<Upload>> uploadListCaptor;
    
    private DynamoUploadDao dao;
    
    @Before
    public void before() {
        dao = new DynamoUploadDao();
        dao.setDdbMapper(mockMapper);
        dao.setHealthCodeRequestedOnIndex(mockIndexHelper);
        
        when(mockIndexHelper.getIndex()).thenReturn(mockIndex);
    }
    
    @Test
    public void createUpload() {
        // execute
        UploadRequest req = createUploadRequest();
        Upload upload = dao.createUpload(req, TEST_STUDY, "fakeHealthCode", null);

        // Validate that our mock DDB mapper was called.
        verify(mockMapper).save(uploadCaptor.capture());
        
        DynamoUpload2 capturedUpload = uploadCaptor.getValue();

        // Validate that our DDB upload object matches our upload request, and that the upload ID matches.
        assertEquals(upload.getUploadId(), capturedUpload.getUploadId());
        assertNull(capturedUpload.getDuplicateUploadId());
        assertEquals(TEST_STUDY.getIdentifier(), capturedUpload.getStudyId());
        assertTrue(capturedUpload.getRequestedOn() > 0);
        assertEquals(UploadStatus.REQUESTED, capturedUpload.getStatus());
        assertEquals(req.getContentLength(), capturedUpload.getContentLength());
        assertEquals(req.getContentMd5(), capturedUpload.getContentMd5());
        assertEquals(req.getContentType(), capturedUpload.getContentType());
        assertEquals(req.getName(), capturedUpload.getFilename());
    }

    @Test
    public void createUploadDupe() {
        // execute
        UploadRequest req = createUploadRequest();
        dao.createUpload(req, TEST_STUDY, "fakeHealthCode", "original-upload-id");

        // Validate that our mock DDB mapper was called.
        verify(mockMapper).save(uploadCaptor.capture());

        DynamoUpload2 capturedUpload = uploadCaptor.getValue();
        
        // Validate key values (study ID, requestedOn) and values from the dupe code path.
        // Everything else is tested in the previous test
        assertEquals("original-upload-id", capturedUpload.getDuplicateUploadId());
        assertEquals(TEST_STUDY.getIdentifier(), capturedUpload.getStudyId());
        assertTrue(capturedUpload.getRequestedOn() > 0);
        assertEquals(UploadStatus.DUPLICATE, capturedUpload.getStatus());
    }

    @Test
    public void getUpload() {
        // mock DDB mapper
        DynamoUpload2 upload = new DynamoUpload2();
        when(mockMapper.load(uploadCaptor.capture())).thenReturn(upload);

        // execute
        Upload retVal = dao.getUpload("test-get-upload");
        assertSame(upload, retVal);

        // validate we passed in the expected key
        assertEquals("test-get-upload", uploadCaptor.getValue().getUploadId());
    }

    @Test
    public void getUploadNotFound() {
        when(mockMapper.load(uploadCaptor.capture())).thenReturn(null);

        // execute
        Exception thrown = null;
        try {
            dao.getUpload("test-get-404");
            fail();
        } catch (NotFoundException ex) {
            thrown = ex;
        }
        assertNotNull(thrown);

        // validate we passed in the expected key
        assertEquals("test-get-404", uploadCaptor.getValue().getUploadId());
    }

    @Test
    public void uploadComplete() {
        // execute
        dao.uploadComplete(UploadCompletionClient.APP, new DynamoUpload2());

        // Verify our mock. We add status=VALIDATION_IN_PROGRESS and uploadDate on save, so only check for those
        // properties.
        verify(mockMapper).save(uploadCaptor.capture());
        assertEquals(UploadStatus.VALIDATION_IN_PROGRESS, uploadCaptor.getValue().getStatus());
        assertEquals(UploadCompletionClient.APP, uploadCaptor.getValue().getCompletedBy());
        assertTrue(uploadCaptor.getValue().getCompletedOn() > 0);

        // There is a slim chance that this will fail if it runs just after midnight.
        assertEquals(LocalDate.now(DateTimeZone.forID("America/Los_Angeles")), uploadCaptor.getValue().getUploadDate());
    }

    @Test
    public void writeValidationStatus() {
        // create input
        DynamoUpload2 upload2 = new DynamoUpload2();
        upload2.setUploadId("test-upload");
        upload2.setValidationMessageList(Collections.<String>emptyList());

        // execute
        dao.writeValidationStatus(upload2, UploadStatus.SUCCEEDED, ImmutableList.of("wrote new"), null);

        // Verify our mock. We set the status and append messages.
        verify(mockMapper).save(uploadCaptor.capture());
        assertEquals(UploadStatus.SUCCEEDED, uploadCaptor.getValue().getStatus());
        assertNull(uploadCaptor.getValue().getRecordId());

        List<String> messageList = uploadCaptor.getValue().getValidationMessageList();
        assertEquals(1, messageList.size());
        assertEquals("wrote new", messageList.get(0));
    }

    @Test
    public void writeValidationStatusOptionalValues() {
        // create input
        DynamoUpload2 upload2 = new DynamoUpload2();
        upload2.setUploadId("test-upload");
        upload2.setValidationMessageList(ImmutableList.of("pre-existing message"));

        // execute
        dao.writeValidationStatus(upload2, UploadStatus.SUCCEEDED, ImmutableList.of("appended this message"),
                "test-record-id");

        // Verify our mock. We set the status and append messages.
        verify(mockMapper).save(uploadCaptor.capture());
        assertEquals(UploadStatus.SUCCEEDED, uploadCaptor.getValue().getStatus());
        assertEquals("test-record-id", uploadCaptor.getValue().getRecordId());

        List<String> messageList = uploadCaptor.getValue().getValidationMessageList();
        assertEquals(2, messageList.size());
        assertEquals("pre-existing message", messageList.get(0));
        assertEquals("appended this message", messageList.get(1));
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void getUploads() {
        String healthCode = "abc";
        DateTime startTime = DateTime.now().minusDays(4);
        DateTime endTime = DateTime.now();
        int pageSize = 50;
        
        // The mock items are not in order, the later one is returned before the earlier one,
        // and the order should be reversed by sorting.
        Item mockItem1 = new Item().withLong("requestedOn", 30000).with("uploadId", UPLOAD_ID);
        Item mockItem2 = new Item().withLong("requestedOn", 10000).with("uploadId", UPLOAD_ID_2);
        
        when(upload1.getRequestedOn()).thenReturn(30000L);
        when(upload1.getUploadId()).thenReturn(UPLOAD_ID);
        when(upload2.getRequestedOn()).thenReturn(10000L);
        when(upload2.getUploadId()).thenReturn(UPLOAD_ID_2);
        
        List<Item> callOrder = Lists.newArrayList(mockItem1, mockItem2);
        doAnswer((invocationOnMock) -> {
            Consumer<Item> consumer = invocationOnMock.getArgumentAt(0, Consumer.class);
            consumer.accept(callOrder.remove(0));
            return null;
        }).when(mockQueryOutcome).forEach(any());

        when(mockIndexHelper.query(any(QuerySpec.class))).thenReturn(lastQueryOutcome);
        when(lastQueryOutcome.getItems()).thenReturn(Lists.newArrayList(mockItem1, mockItem2));
        
        Map<String,List<Object>> batchLoadMap = new ImmutableMap.Builder<String,List<Object>>()
                .put(UPLOAD_ID, Lists.newArrayList(upload1))
                .put(UPLOAD_ID_2, Lists.newArrayList(upload2)).build();
        
        when(mockMapper.batchLoad(any(List.class))).thenReturn(batchLoadMap);
        
        ForwardCursorPagedResourceList<Upload> page = dao.getUploads(healthCode, startTime, endTime, pageSize, null);
        
        verify(mockIndexHelper).query(querySpecCaptor.capture());
        QuerySpec mockSpec = querySpecCaptor.getValue();
        assertEquals(new Integer(51), mockSpec.getMaxPageSize());
        assertEquals(healthCode, mockSpec.getHashKey().getValue());
        
        verify(mockMapper).batchLoad(uploadListCaptor.capture());
        List<Upload> uploads = uploadListCaptor.getValue();
        assertEquals(2, uploads.size());
        
        // These have been sorted.
        assertEquals(2, page.getItems().size());
        assertEquals(10000, page.getItems().get(0).getRequestedOn());
        assertEquals(30000, page.getItems().get(1).getRequestedOn());
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
