package org.sagebionetworks.bridge.validators;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.studies.StudyConsentForm;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.xml.sax.SAXException;

@Component
public class StudyConsentValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return StudyConsentForm.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        StudyConsentForm consent = (StudyConsentForm)object;

        if (StringUtils.isBlank(consent.getDocumentContent())) {
            errors.rejectValue("documentContent", "is null or blank");
            return;
        }
        try {
            InputStream stream = new ByteArrayInputStream(consent.getDocumentContent().getBytes(StandardCharsets.UTF_8));
            DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            parser.parse(stream);
        } catch(ParserConfigurationException | IOException e) {
            throw new BridgeServiceException(e);
        } catch (SAXException e) {
            errors.reject(e.getMessage());
        }
    }

}