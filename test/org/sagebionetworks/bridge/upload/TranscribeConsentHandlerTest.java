package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;

import org.junit.Test;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;

public class TranscribeConsentHandlerTest {
    private static final String TEST_HEALTHCODE = "test-healthcode";
    private static final String TEST_EXTERNAL_ID = "test-external-id";

    @Test
    public void test() throws Exception {
        // mock options service
        ParticipantOptionsService mockOptionsService = mock(ParticipantOptionsService.class);
        when(mockOptionsService.getAllParticipantOptions(TEST_HEALTHCODE)).thenReturn(ImmutableMap.of(
                ParticipantOption.SHARING_SCOPE, ParticipantOption.SharingScope.SPONSORS_AND_PARTNERS.name(),
                ParticipantOption.EXTERNAL_IDENTIFIER, TEST_EXTERNAL_ID));

        TranscribeConsentHandler handler = new TranscribeConsentHandler();
        handler.setOptionsService(mockOptionsService);

        // set up context - handler expects Upload and RecordBuilder
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setHealthCode(TEST_HEALTHCODE);

        HealthDataRecordBuilder recordBuilder = new DynamoHealthDataRecord.Builder();

        UploadValidationContext context = new UploadValidationContext();
        context.setUpload(upload);
        context.setHealthDataRecordBuilder(recordBuilder);

        // execute
        handler.handle(context);

        // validate
        HealthDataRecordBuilder outputRecordBuilder = context.getHealthDataRecordBuilder();
        assertSame(recordBuilder, outputRecordBuilder);
        assertEquals(ParticipantOption.SharingScope.SPONSORS_AND_PARTNERS, outputRecordBuilder.getUserSharingScope());
        assertEquals(TEST_EXTERNAL_ID, outputRecordBuilder.getUserExternalId());
    }
}
