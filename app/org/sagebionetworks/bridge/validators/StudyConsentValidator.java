package org.sagebionetworks.bridge.validators;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.studies.StudyConsentForm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.xml.sax.SAXException;

import com.newrelic.agent.deps.com.google.common.collect.Maps;

@Component
public class StudyConsentValidator implements Validator {

    private String consentBodyTemplate;
    
    @Value("classpath:study-defaults/consent-page.xhtml")
    final void setConsentBodyTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.consentBodyTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    
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
        // Assemble the final document and validate that it parses as XML. Otherwise the PDF generator
        // will throw an error.
        try {
            Map<String,String> map = Maps.newHashMap();
            map.put("consent.body", consent.getDocumentContent());
            String mergedDocument = BridgeUtils.resolveTemplate(consentBodyTemplate, map);
            
            InputStream stream = new ByteArrayInputStream(mergedDocument.getBytes(StandardCharsets.UTF_8));
            DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            parser.parse(stream);
        } catch(ParserConfigurationException | IOException e) {
            throw new BridgeServiceException(e);
        } catch (SAXException e) {
            errors.reject(e.getMessage());
        }
    }

}