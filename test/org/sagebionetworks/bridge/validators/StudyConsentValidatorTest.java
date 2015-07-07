package org.sagebionetworks.bridge.validators;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.studies.StudyConsentForm;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;

public class StudyConsentValidatorTest {

    private Resource resource;
    
    @Before
    public void before() {
        resource = new AbstractResource() {
            @Override
            public InputStream getInputStream() throws IOException {
                return new FileInputStream("conf/study-defaults/consent-page.xhtml");
            }
            @Override
            public String getDescription() {
                return null;
            }
        };
    }
    
    @After
    public void after() throws IOException {
        resource.getInputStream().close();
    }
    
    @Test(expected = InvalidEntityException.class)
    public void detectsInvalidXML() throws Exception {
        StudyConsentValidator validator = new StudyConsentValidator();
        validator.setConsentBodyTemplate(resource);
        
        StudyConsentForm form = new StudyConsentForm("<p>Definitely broken markup </xml>");
        
        Validate.entityThrowingException(validator, form);
    }

}
