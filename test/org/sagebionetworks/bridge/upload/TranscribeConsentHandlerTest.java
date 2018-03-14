package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

@RunWith(MockitoJUnitRunner.class)
public class TranscribeConsentHandlerTest {
    private static final String TEST_HEALTHCODE = "test-healthcode";
    private static final String TEST_EXTERNAL_ID = "test-external-id";
    private static final Set<String> TEST_USER_GROUPS = ImmutableSet.of("test-group1","test-group2");

    @Mock
    private AccountDao mockAccountDao;
    
    @Mock
    private Account mockAccount;
    
    @Test
    public void test() {
        when(mockAccount.getExternalId()).thenReturn(TEST_EXTERNAL_ID);
        when(mockAccount.getSharingScope()).thenReturn(SharingScope.SPONSORS_AND_PARTNERS);
        when(mockAccount.getDataGroups()).thenReturn(TEST_USER_GROUPS);
        when(mockAccountDao.getAccount(any())).thenReturn(mockAccount);
        
        HealthDataRecord record = HealthDataRecord.create();
        HealthDataRecord outputRecord = setupContextAndRunHandler(record);
        
        assertSame(record, outputRecord);
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, outputRecord.getUserSharingScope());
        assertEquals(TEST_EXTERNAL_ID, outputRecord.getUserExternalId());
        assertEquals(Sets.newHashSet("test-group1","test-group2"), outputRecord.getUserDataGroups());
    }

    @Test
    public void testNoParticipantOptions() {
        // account will return null for all values (nothing mocked)

        HealthDataRecord record = HealthDataRecord.create();
        HealthDataRecord outputRecord = setupContextAndRunHandler(record);

        assertSame(record, outputRecord);
        assertEquals(SharingScope.NO_SHARING, outputRecord.getUserSharingScope());
        assertNull(outputRecord.getUserExternalId());
        assertNull(outputRecord.getUserDataGroups());
    }

    @Test
    public void emptyStringSetConvertedCorrectly() {
        when(mockAccount.getDataGroups()).thenReturn(ImmutableSet.of());
        when(mockAccountDao.getAccount(any())).thenReturn(mockAccount);

        HealthDataRecord record = HealthDataRecord.create();
        HealthDataRecord outputRecord = setupContextAndRunHandler(record);
        
        assertNull(outputRecord.getUserDataGroups());
    }
    
    private HealthDataRecord setupContextAndRunHandler(HealthDataRecord record) {
        TranscribeConsentHandler handler = new TranscribeConsentHandler();
        handler.setAccountDao(mockAccountDao);

        // set up context - handler expects Health Code and RecordBuilder
        UploadValidationContext context = new UploadValidationContext();
        context.setStudy(TestConstants.TEST_STUDY);
        context.setHealthCode(TEST_HEALTHCODE);
        context.setHealthDataRecord(record);

        // execute
        handler.handle(context);
        return context.getHealthDataRecord();
    }
}
