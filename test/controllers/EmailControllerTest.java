package controllers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static play.test.Helpers.contentAsString;

import org.junit.Test;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.services.HealthCodeService;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;
import org.sagebionetworks.bridge.services.ParticipantOptionsServiceImpl;
import org.sagebionetworks.bridge.services.StudyService;

import play.mvc.Http;
import play.mvc.Result;

import java.util.Map;

import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.Maps;

public class EmailControllerTest {

    private ParticipantOptionsService optionsService;
    
    private AccountDao accountDao;
    
    private Study study;
    
    private Map<String,String[]> map(String[] values) {
        Map<String,String[]> map = Maps.newHashMap();
        for (int i=0; i <= values.length-2; i+=2) {
            map.put(values[i], new String[] { values[i+1] });
        }
        return map;
    }
    
    private void mockContext(String... values) throws Exception {
        Map<String,String[]> map = map(values);
        
        Http.Request request = mock(Http.Request.class);
        when(request.queryString()).thenReturn(map);

        Http.Context context = mock(Http.Context.class);
        when(context.request()).thenReturn(request);

        Http.Context.current.set(context);
    }
    
    private EmailController createController() {
        optionsService = mock(ParticipantOptionsServiceImpl.class);
        
        Account account = mock(Account.class);
        when(account.getHealthId()).thenReturn("healthId");
        
        study = new DynamoStudy();
        study.setIdentifier("api");
        
        accountDao = mock(AccountDao.class);
        when(accountDao.getAccount(study, "bridge-testing@sagebase.org")).thenReturn(account);
        
        HealthId healthId = mock(HealthId.class);
        when(healthId.getCode()).thenReturn("healthCode");
        
        HealthCodeService healthCodeService = mock(HealthCodeService.class);
        when(healthCodeService.getMapping("healthId")).thenReturn(healthId);
        
        StudyService studyService = mock(StudyService.class);
        when(studyService.getStudy("api")).thenReturn(study);
        when(studyService.getStudy((String)null)).thenThrow(new EntityNotFoundException(Study.class));
        
        BridgeConfig config = mock(BridgeConfig.class);
        when(config.getEmailUnsubscribeToken()).thenReturn("unsubscribeToken");
        
        EmailController controller = spy(new EmailController());
        controller.setParticipantOptionsService(optionsService);
        controller.setStudyService(studyService);
        controller.setAccountDao(accountDao);
        controller.setHealthCodeService(healthCodeService);
        controller.setBridgeConfig(config);

        return controller;
    }
    
    @Test
    public void updatesOptionToTurnOffEmail() throws Exception {
        mockContext("data[email]", "bridge-testing@sagebase.org", "study", "api", "token", "unsubscribeToken");
        
        EmailController controller = createController();
        controller.unsubscribeFromEmail();
        
        verify(optionsService).setOption(study, "healthCode", 
            ParticipantOption.EMAIL_NOTIFICATIONS, "false");
    }
    
    @Test
    public void noStudyThrowsException() throws Exception {
        mockContext("data[email]", "bridge-testing@sagebase.org", "token", "unsubscribeToken");
        
        EmailController controller = createController();
        Result result = controller.unsubscribeFromEmail();
        contentAsString(result).contains("Study not found");
    }
    
    @Test
    public void noEmailThrowsException() throws Exception {
        mockContext("study", "api", "token", "unsubscribeToken");
        
        EmailController controller = createController();
        Result result = controller.unsubscribeFromEmail();
        contentAsString(result).contains("Email not found");
    }
    
    @Test
    public void noAccountThrowsException() throws Exception {
        mockContext("data[email]", "bridge-testing@sagebase.org", "study", "api", "token", "unsubscribeToken");
        
        EmailController controller = createController();
        when(accountDao.getAccount(study, "bridge-testing@sagebase.org")).thenReturn(null);
        
        Result result = controller.unsubscribeFromEmail();
        contentAsString(result).contains("Account not found");
    }
    
    @Test
    public void cannotMakeCallWithoutToken() throws Exception {
        mockContext("data[email]", "bridge-testing@sagebase.org", "study", "api");
        
        EmailController controller = createController();
        Result result = controller.unsubscribeFromEmail();
        contentAsString(result).contains("Not authorized");
    }
    
}
