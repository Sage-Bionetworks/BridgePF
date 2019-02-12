package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Charsets;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.s3.S3Helper;

public class UploadFileHelperUploadJsonAttachmentTest {
    private static final String FIELD_NAME = "field-name";
    private static final String UPLOAD_ID = "upload-id";
    private static final String EXPECTED_ATTACHMENT_NAME = UPLOAD_ID + '-' + FIELD_NAME;

    private S3Helper mockS3Helper;
    private UploadFileHelper uploadFileHelper;

    @Before
    public void before() {
        mockS3Helper = mock(S3Helper.class);
        uploadFileHelper = new UploadFileHelper();
        uploadFileHelper.setS3Helper(mockS3Helper);
    }

    @Test
    public void successCase() throws Exception {
        // Normally this will be an array or an object, but for the purpose of this test, use a TextNode.
        JsonNode result = uploadFileHelper.uploadJsonNodeAsAttachment(TextNode.valueOf("dummy content"), UPLOAD_ID,
                FIELD_NAME);
        assertEquals(EXPECTED_ATTACHMENT_NAME, result.textValue());

        // Validate uploaded content. The text is quoted because when we serialize a JSON string, it quotes the JSON
        // string.
        verify(mockS3Helper).writeBytesToS3(UploadFileHelper.ATTACHMENT_BUCKET, EXPECTED_ATTACHMENT_NAME,
                "\"dummy content\"".getBytes(Charsets.UTF_8));
    }

    @Test(expected = UploadValidationException.class)
    public void errorCase() throws Exception {
        doThrow(IOException.class).when(mockS3Helper).writeBytesToS3(any(), any(), any(byte[].class));
        uploadFileHelper.uploadJsonNodeAsAttachment(TextNode.valueOf("dummy content"), UPLOAD_ID, FIELD_NAME);
    }
}
