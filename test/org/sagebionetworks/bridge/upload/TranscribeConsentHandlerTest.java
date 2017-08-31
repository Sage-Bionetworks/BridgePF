package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.junit.Test;

import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
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
        
        HealthDataRecord record = HealthDataRecord.create();
        HealthDataRecord outputRecord = setupContextAndRunHandler(record, mockOptionsService);
        
        assertSame(record, outputRecord);
        assertEquals(ParticipantOption.SharingScope.SPONSORS_AND_PARTNERS, outputRecord.getUserSharingScope());
        assertEquals(TEST_EXTERNAL_ID, outputRecord.getUserExternalId());
        assertEquals(Sets.newHashSet("test-group1","test-group2"), outputRecord.getUserDataGroups());
    }

    @Test
    public void testNoParticipantOptions() {
        // mock options service
        ParticipantOptionsService mockOptionsService = mock(ParticipantOptionsService.class);
        when(mockOptionsService.getOptions(TEST_HEALTHCODE)).thenReturn(new ParticipantOptionsLookup(Maps.newHashMap()));

        HealthDataRecord record = HealthDataRecord.create();
        HealthDataRecord outputRecord = setupContextAndRunHandler(record, mockOptionsService);

        assertSame(record, outputRecord);
        assertEquals(ParticipantOption.SharingScope.NO_SHARING, outputRecord.getUserSharingScope());
        assertNull(outputRecord.getUserExternalId());
        assertNull(outputRecord.getUserDataGroups());
    }

    @Test
    public void emptyStringSetConvertedCorrectly() {
        ParticipantOptionsService mockOptionsService = mock(ParticipantOptionsService.class);
        when(mockOptionsService.getOptions(TEST_HEALTHCODE)).thenReturn(new ParticipantOptionsLookup(ImmutableMap.of(
                ParticipantOption.DATA_GROUPS.name(), "")));

        HealthDataRecord record = HealthDataRecord.create();
        HealthDataRecord outputRecord = setupContextAndRunHandler(record, mockOptionsService);
        
        assertNull(outputRecord.getUserDataGroups());
    }
    
    @Test
    public void setOfOneStringConvertedCorrectly() {
        ParticipantOptionsService mockOptionsService = mock(ParticipantOptionsService.class);
        when(mockOptionsService.getOptions(TEST_HEALTHCODE)).thenReturn(new ParticipantOptionsLookup(
                ImmutableMap.of(ParticipantOption.DATA_GROUPS.name(), "group1")));

        HealthDataRecord record = HealthDataRecord.create();
        HealthDataRecord outputRecord = setupContextAndRunHandler(record, mockOptionsService);
        
        assertEquals(Sets.newHashSet("group1"), outputRecord.getUserDataGroups());
    }
    
    private HealthDataRecord setupContextAndRunHandler(HealthDataRecord record,
            ParticipantOptionsService optsService) {
        TranscribeConsentHandler handler = new TranscribeConsentHandler();
        handler.setOptionsService(optsService);

        // set up context - handler expects Health Code and RecordBuilder
        UploadValidationContext context = new UploadValidationContext();
        context.setHealthCode(TEST_HEALTHCODE);
        context.setHealthDataRecord(record);

        // execute
        handler.handle(context);
        return context.getHealthDataRecord();
    }
}
