package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.studies.Study;

@RunWith(MockitoJUnitRunner.class)
public class ParticipantServiceTest {

    private static final Study STUDY = new DynamoStudy();
    
    private ParticipantService participantService;
    
    @Mock
    private AccountDao accountDao;
    
    @Before
    public void before() {
        participantService = new ParticipantService();
        participantService.setAccountDao(accountDao);
    }
    
    @Test
    public void getPagedAccountSummaries() {
        participantService.getPagedAccountSummaries(STUDY, 1100, 50);
        
        verify(accountDao).getPagedAccountSummaries(STUDY, 1100, 50); 
    }
    
    @Test(expected = NullPointerException.class)
    public void badStudyRejected() {
        participantService.getPagedAccountSummaries(null, 0, 100);
    }
    
    @Test(expected = BadRequestException.class)
    public void offsetByCannotBeNegative() {
        participantService.getPagedAccountSummaries(STUDY, -1, 100);
    }
    
    @Test(expected = BadRequestException.class)
    public void limitToCannotBeNegative() {
        participantService.getPagedAccountSummaries(STUDY, 0, -100);
    }
    
    @Test(expected = BadRequestException.class)
    public void limitToCannotBeGreaterThan250() {
        participantService.getPagedAccountSummaries(STUDY, 0, 251);
    }
}
