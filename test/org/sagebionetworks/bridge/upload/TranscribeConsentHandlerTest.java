package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import org.junit.Test;

import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;

public class TranscribeConsentHandlerTest {
    private static final String TEST_HEALTHCODE = "test-healthcode";
    private static final String TEST_EXTERNAL_ID = "test-external-id";
    private static final String TEST_USER_GROUPS = "test-group1,test-group2";

    @Test
    public void test() {
        // mock options service
        ParticipantOptionsService mockOptionsService = mock(ParticipantOptionsService.class);
        when(mockOptionsService.getOptions(TEST_HEALTHCODE)).thenReturn(new ParticipantOptionsLookup(
            ImmutableMap.of(
                ParticipantOption.SHARING_SCOPE.name(), ParticipantOption.SharingScope.SPONSORS_AND_PARTNERS.name(),
                ParticipantOption.EXTERNAL_IDENTIFIER.name(), TEST_EXTERNAL_ID,
                ParticipantOption.DATA_GROUPS.name(), TEST_USER_GROUPS)
            )
        );
        
        HealthDataRecordBuilder recordBuilder = new DynamoHealthDataRecord.Builder();
        HealthDataRecordBuilder outputRecordBuilder = setupContextAndRunHandler(recordBuilder, mockOptionsService);
        
        assertSame(recordBuilder, outputRecordBuilder);
        assertEquals(ParticipantOption.SharingScope.SPONSORS_AND_PARTNERS, outputRecordBuilder.getUserSharingScope());
        assertEquals(TEST_EXTERNAL_ID, outputRecordBuilder.getUserExternalId());
        assertEquals(Sets.newHashSet("test-group1","test-group2"), outputRecordBuilder.getUserDataGroups());
    }

    @Test
    public void testNoParticipantOptions() {
        // mock options service
        ParticipantOptionsService mockOptionsService = mock(ParticipantOptionsService.class);
        when(mockOptionsService.getOptions(TEST_HEALTHCODE)).thenReturn(new ParticipantOptionsLookup(null));

        HealthDataRecordBuilder recordBuilder = new DynamoHealthDataRecord.Builder();
        HealthDataRecordBuilder outputRecordBuilder = setupContextAndRunHandler(recordBuilder, mockOptionsService);
        
        assertSame(recordBuilder, outputRecordBuilder);
        assertEquals(ParticipantOption.SharingScope.NO_SHARING, outputRecordBuilder.getUserSharingScope());
        assertNull(outputRecordBuilder.getUserExternalId());
        assertNull(outputRecordBuilder.getUserDataGroups());
    }

    @Test
    public void emptyStringSetConvertedCorrectly() {
        ParticipantOptionsService mockOptionsService = mock(ParticipantOptionsService.class);
        when(mockOptionsService.getOptions(TEST_HEALTHCODE)).thenReturn(new ParticipantOptionsLookup(ImmutableMap.of(
                ParticipantOption.DATA_GROUPS.name(), "")));
        
        HealthDataRecordBuilder recordBuilder = new DynamoHealthDataRecord.Builder();
        HealthDataRecordBuilder outputRecordBuilder = setupContextAndRunHandler(recordBuilder, mockOptionsService);
        
        assertNull(outputRecordBuilder.getUserDataGroups());
    }
    
    @Test
    public void setOfOneStringConvertedCorrectly() {
        ParticipantOptionsService mockOptionsService = mock(ParticipantOptionsService.class);
        when(mockOptionsService.getOptions(TEST_HEALTHCODE)).thenReturn(new ParticipantOptionsLookup(
                ImmutableMap.of(ParticipantOption.DATA_GROUPS.name(), "group1")));
        
        HealthDataRecordBuilder recordBuilder = new DynamoHealthDataRecord.Builder();
        HealthDataRecordBuilder outputRecordBuilder = setupContextAndRunHandler(recordBuilder, mockOptionsService);
        
        assertEquals(Sets.newHashSet("group1"), outputRecordBuilder.getUserDataGroups());
    }
    
    private HealthDataRecordBuilder setupContextAndRunHandler(HealthDataRecordBuilder recordBuilder,
            ParticipantOptionsService optsService) {
        TranscribeConsentHandler handler = new TranscribeConsentHandler();
        handler.setOptionsService(optsService);

        // set up context - handler expects Upload and RecordBuilder
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setHealthCode(TEST_HEALTHCODE);

        UploadValidationContext context = new UploadValidationContext();
        context.setUpload(upload);
        context.setHealthDataRecordBuilder(recordBuilder);

        // execute
        handler.handle(context);
        return context.getHealthDataRecordBuilder();
    }
}
