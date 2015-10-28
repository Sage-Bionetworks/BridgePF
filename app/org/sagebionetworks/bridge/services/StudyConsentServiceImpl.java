package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Document.OutputSettings.Syntax;
import org.jsoup.nodes.Entities.EscapeMode;
import org.jsoup.safety.Whitelist;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyConsentForm;
import org.sagebionetworks.bridge.models.studies.StudyConsentView;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.validators.StudyConsentValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.validation.Validator;
import org.xhtmlrenderer.pdf.ITextRenderer;

import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.google.common.collect.Maps;
import com.lowagie.text.DocumentException;

@Component
public class StudyConsentServiceImpl implements StudyConsentService {

    private static Logger logger = LoggerFactory.getLogger(StudyConsentServiceImpl.class);
    
    private Validator validator;
    private StudyConsentDao studyConsentDao;
    private S3Helper s3Helper;
    private static final String CONSENTS_BUCKET = BridgeConfigFactory.getConfig().getConsentsBucket();
    private static final String PUBLICATIONS_BUCKET = BridgeConfigFactory.getConfig().getHostnameWithPostfix("docs");
    private String fullPageTemplate;
    
    @Value("classpath:study-defaults/consent-unsigned-page.xhtml")
    final void setConsentTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.fullPageTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    @Autowired
    public void setValidator(StudyConsentValidator validator) {
        this.validator = validator;
    }
    
    @Autowired
    public void setStudyConsentDao(StudyConsentDao studyConsentDao) {
        this.studyConsentDao = studyConsentDao;
    }
    
    @Resource(name = "s3ConsentsHelper")
    public void set(S3Helper helper) {
        this.s3Helper = helper;
    }
    
    @Override
    public StudyConsentView addConsent(StudyIdentifier studyIdentifier, StudyConsentForm form) {
        checkNotNull(studyIdentifier, "StudyIdentifier is null");
        checkNotNull(form, "Study consent is null");
        
        String sanitizedContent = sanitizeHTML(form.getDocumentContent());
        Validate.entityThrowingException(validator, new StudyConsentForm(sanitizedContent));
        
        DateTime createdOn = DateUtils.getCurrentDateTime();
        String storagePath = studyIdentifier.getIdentifier() + "." + createdOn.getMillis();
        
        try {
            s3Helper.writeBytesToS3(CONSENTS_BUCKET, storagePath, sanitizedContent.getBytes());
            StudyConsent consent = studyConsentDao.addConsent(studyIdentifier, storagePath, createdOn);
            return new StudyConsentView(consent, sanitizedContent);
        } catch(Throwable t) {
            throw new BridgeServiceException(t);
        }
    }

    @Override
    public StudyConsentView getActiveConsent(StudyIdentifier studyIdentifier) {
        checkNotNull(studyIdentifier, "StudyIdentifier is null");
        
        StudyConsent consent = studyConsentDao.getActiveConsent(studyIdentifier);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        String documentContent = loadDocumentContent(consent);
        return new StudyConsentView(consent, documentContent);
    }
    
    @Override
    public StudyConsentView getMostRecentConsent(StudyIdentifier studyIdentifier) {
        checkNotNull(studyIdentifier, "StudyIdentifier is null");
        
        StudyConsent consent = studyConsentDao.getMostRecentConsent(studyIdentifier);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        String documentContent = loadDocumentContent(consent);
        return new StudyConsentView(consent, documentContent);
    }

    @Override
    public List<StudyConsent> getAllConsents(StudyIdentifier studyIdentifier) {
        checkNotNull(studyIdentifier, "StudyIdentifier is null");
        
        List<StudyConsent> consents = studyConsentDao.getConsents(studyIdentifier);
        if (consents == null || consents.isEmpty()) {
            throw new BadRequestException("There are no consent records.");
        }
        return consents;
    }

    @Override
    public StudyConsentView getConsent(StudyIdentifier studyIdentifier, long timestamp) {
        checkNotNull(studyIdentifier, "StudyIdentifier is null");
        checkArgument(timestamp > 0, "Timestamp is 0");
        
        StudyConsent consent = studyConsentDao.getConsent(studyIdentifier, timestamp);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        String documentContent = loadDocumentContent(consent);
        return new StudyConsentView(consent, documentContent);
    }

    @Override
    public StudyConsentView publishConsent(Study study, long timestamp) {
        checkNotNull(study, "Study is null");
        checkArgument(timestamp > 0, "Timestamp is 0");
        
        StudyConsent consent = studyConsentDao.getConsent(study.getStudyIdentifier(), timestamp);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        // Only if we can publish the document, do we mark it as published in the database.
        String documentContent = loadDocumentContent(consent);
        try {
            publishFormatsToS3(study, documentContent);
            consent = studyConsentDao.publish(consent);
        } catch(IOException | DocumentException e) {
            throw new BridgeServiceException(e.getMessage());
        }
        return new StudyConsentView(consent, documentContent);
    }
    
    private String loadDocumentContent(StudyConsent consent) {
        try {
            return s3Helper.readS3FileAsString(CONSENTS_BUCKET, consent.getStoragePath());
        } catch(IOException ioe) {
            logger.error("Failure loading storagePath: " + consent.getStoragePath());
            throw new BridgeServiceException(ioe);
        }
    }
    
    private String sanitizeHTML(String documentContent) {
        documentContent = Jsoup.clean(documentContent, Whitelist.relaxed());
        Document document = Jsoup.parseBodyFragment(documentContent);
        document.outputSettings().escapeMode(EscapeMode.xhtml)
            .prettyPrint(false).syntax(Syntax.xml).charset("UTF-8");
        return document.body().html();
    }
    
    private void publishFormatsToS3(Study study, String bodyTemplate) throws DocumentException, IOException {
        Map<String,String> map = Maps.newHashMap();
        map.put("studyName", study.getName());
        map.put("supportEmail", study.getSupportEmail());
        map.put("technicalEmail", study.getTechnicalEmail());
        map.put("sponsorName", study.getSponsorName());
        String resolvedHTML = BridgeUtils.resolveTemplate(bodyTemplate, map);

        map = Maps.newHashMap();
        map.put("studyName", study.getName());
        map.put("consent.body", resolvedHTML);
        resolvedHTML = BridgeUtils.resolveTemplate(fullPageTemplate, map);
        
        String key = study.getIdentifier()+"/consent.html";
        byte[] bytes = resolvedHTML.getBytes(Charset.forName(("UTF-8")));
        s3Helper.writeBytesToPublicS3(PUBLICATIONS_BUCKET, key, bytes, MimeType.HTML);
        
        // Now create and post a PDF version !
        try (ByteArrayBuilder buffer = new ByteArrayBuilder();) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(resolvedHTML);
            renderer.layout();
            renderer.createPDF(buffer);
            buffer.flush();
            
            key = study.getIdentifier()+"/consent.pdf";
            s3Helper.writeBytesToPublicS3(PUBLICATIONS_BUCKET, key, buffer.toByteArray(), MimeType.PDF);
        }
    }

}