package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.List;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Document.OutputSettings.Syntax;
import org.jsoup.nodes.Entities.EscapeMode;
import org.jsoup.safety.Whitelist;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.DateUtils;
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
import org.springframework.stereotype.Component;
import org.springframework.validation.Validator;

@Component
public class StudyConsentServiceImpl implements StudyConsentService {

    private static Logger logger = LoggerFactory.getLogger(StudyConsentServiceImpl.class);
    
    private Validator validator;
    private StudyConsentDao studyConsentDao;
    private S3Helper s3Helper;
    private static final String BUCKET = BridgeConfigFactory.getConfig().getConsentsBucket();

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
            s3Helper.writeBytesToS3(BUCKET, storagePath, sanitizedContent.getBytes());
            StudyConsent consent = studyConsentDao.addConsent(studyIdentifier, storagePath, createdOn);
            return new StudyConsentView(consent, sanitizedContent);
        } catch(Throwable t) {
            throw new BridgeServiceException(t);
        }
    }

    @Override
    public StudyConsentView getActiveConsent(StudyIdentifier studyIdentifier) {
        checkNotNull(studyIdentifier, "StudyIdentifier is null");
        
        StudyConsent consent = studyConsentDao.getConsent(studyIdentifier);
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
    public StudyConsentView activateConsent(StudyIdentifier studyIdentifier, long timestamp) {
        checkNotNull(studyIdentifier, "StudyIdentifier is null");
        checkArgument(timestamp > 0, "Timestamp is 0");
        
        StudyConsent consent = studyConsentDao.getConsent(studyIdentifier, timestamp);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        consent = studyConsentDao.activate(consent);
        String documentContent = loadDocumentContent(consent);
        return new StudyConsentView(consent, documentContent);
    }
    
    private String loadDocumentContent(StudyConsent consent) {
        try {
            logger.info("Loading S3 key: " + consent.getStoragePath());
            return s3Helper.readS3FileAsString(BUCKET, consent.getStoragePath());
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
}