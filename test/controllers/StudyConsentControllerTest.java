package controllers;

import org.junit.*;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StudyConsentControllerTest {

    @Test(expected = BridgeServiceException.class)
    public void mustBeAdmin() {
        
    }
    
    
    
}
