package org.sagebionetworks.bridge.validators;

import java.io.FileInputStream;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.studies.StudyConsentForm;

public class StudyConsentValidatorTest {

    @Test(expected = InvalidEntityException.class)
    public void detectsInvalidXML() throws Exception {
        StudyConsentValidator validator = new StudyConsentValidator();
        validator.setConsentBodyTemplate(IOUtils.toString(new FileInputStream("conf/study-defaults/consent-page.xhtml")));
        
        StudyConsentForm form = new StudyConsentForm("<p>Definitely broken markup </xml>");
        
        Validate.entityThrowingException(validator, form);
    }

}
