package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Resource;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
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
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentForm;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
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
public class StudyConsentService {

    private static Logger logger = LoggerFactory.getLogger(StudyConsentService.class);
    
    private Validator validator;
    private StudyConsentDao studyConsentDao;
    private SubpopulationService subpopService;
    private AmazonS3Client s3Client;
    private S3Helper s3Helper;
    private static final String CONSENTS_BUCKET = BridgeConfigFactory.getConfig().getConsentsBucket();
    private static final String PUBLICATIONS_BUCKET = BridgeConfigFactory.getConfig().getHostnameWithPostfix("docs");
    private String fullPageTemplate;
    
    @Value("classpath:study-defaults/consent-unsigned-page.xhtml")
    final void setConsentTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.fullPageTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Autowired
    final void setValidator(StudyConsentValidator validator) {
        this.validator = validator;
    }
    @Autowired
    final void setStudyConsentDao(StudyConsentDao studyConsentDao) {
        this.studyConsentDao = studyConsentDao;
    }
    @Autowired
    final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }
    /**
     * S3 client. We need to use the S3 client to call writeBytesToPublicS3(), which wasn't migrated to bridge-base
     * because it references BridgePF-specific classes.
     */
    @Resource(name = "s3ConsentsClient")
    final void setS3Client(AmazonS3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Resource(name = "s3ConsentsHelper")
    final void setS3Helper(S3Helper helper) {
        this.s3Helper = helper;
    }
    
    /**
     * Adds a new consent document to the study, and sets that consent document as active.
     *
     * @param subpopGuid
     *            the subpopulation associated with this consent
     * @param form
     *            form filled out by researcher including the path to the consent document and the minimum age required
     *            to consent.
     * @return the added consent document of type StudyConsent along with its document content
     */
    public StudyConsentView addConsent(SubpopulationGuid subpopGuid, StudyConsentForm form) {
        checkNotNull(subpopGuid);
        checkNotNull(form);
        
        String sanitizedContent = sanitizeHTML(form.getDocumentContent());
        Validate.entityThrowingException(validator, new StudyConsentForm(sanitizedContent));

        DateTime createdOn = DateUtils.getCurrentDateTime();
        String storagePath = subpopGuid.getGuid() + "." + createdOn.getMillis();
        try {
            s3Helper.writeBytesToS3(CONSENTS_BUCKET, storagePath, sanitizedContent.getBytes());
            StudyConsent consent = studyConsentDao.addConsent(subpopGuid, storagePath, createdOn);
            return new StudyConsentView(consent, sanitizedContent);
        } catch(Throwable t) {
            throw new BridgeServiceException(t);
        }
    }

    /**
     * Gets the currently active consent document for the study.
     *
     * @param studyIdentifier
     *          the study this subpopulation is found in.
     * @param subpop
     *          the subpopulation associated with this consent
     * @return the currently active StudyConsent along with its document content
     */
    // NOTE: After migrating publication consent timestamp to the subpopulation, this method can
    // go away, as it's just a variant of getConsent() with a special timestamp.
    public StudyConsentView getActiveConsent(StudyIdentifier studyIdentifier, Subpopulation subpop) {
        checkNotNull(studyIdentifier);
        checkNotNull(subpop);
        
        StudyConsent consent = null;
        if (subpop.getPublishedConsentCreatedOn() > 0L) {
            consent = studyConsentDao.getConsent(subpop.getGuid(), subpop.getPublishedConsentCreatedOn());
        }
        if (consent == null) {
            consent = studyConsentDao.getActiveConsent(subpop.getGuid());
            if (consent != null) {
                subpop.setPublishedConsentCreatedOn(consent.getCreatedOn());
            }
        }
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        String documentContent = loadDocumentContent(consent);
        return new StudyConsentView(consent, documentContent);
    }
    
    /**
     * Gets the most recently created consent document for the study.
     *
     * @param subpopGuid
     *            the subpopulation associated with this consent
     * @return the most recent StudyConsent along with its document content
     */
    public StudyConsentView getMostRecentConsent(SubpopulationGuid subpopGuid) {
        checkNotNull(subpopGuid);
        
        StudyConsent consent = studyConsentDao.getMostRecentConsent(subpopGuid);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        String documentContent = loadDocumentContent(consent);
        return new StudyConsentView(consent, documentContent);
    }

    /**
     * Get all added consent documents for the study.
     *
     * @param subpopGuid
     *            the subpopulation associated with this consent
     * @return list of all consent documents associated with study along with its document content
     */
    public List<StudyConsent> getAllConsents(SubpopulationGuid subpopGuid) {
        checkNotNull(subpopGuid);
        
        return studyConsentDao.getConsents(subpopGuid);
    }

    /**
     * Gets the consent document associated with the study created at the
     * specified timestamp.
     *
     * @param subpopGuid
     *            the subpopulation associated with this consent
     * @param timestamp
     *            time the consent document was added to the database.
     * @return the specified consent document along with its document content
     */
    public StudyConsentView getConsent(SubpopulationGuid subpopGuid, long timestamp) {
        checkNotNull(subpopGuid);
        checkArgument(timestamp > 0, "Timestamp is 0");
        
        StudyConsent consent = studyConsentDao.getConsent(subpopGuid, timestamp);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        String documentContent = loadDocumentContent(consent);
        return new StudyConsentView(consent, documentContent);
    }

    /**
     * Set the specified consent document as active, setting all other consent documents 
     * as inactive.
     *
     * @param study
     *            study for this consent
     * @param subpop
     *            the subpopulation associated with this consent
     * @param timestamp
     *            time the consent document was added to the database.
     * @return the activated consent document along with its document content
     */
    public StudyConsentView publishConsent(Study study, Subpopulation subpop, long timestamp) {
        checkNotNull(study);
        checkNotNull(subpop);
        checkArgument(timestamp > 0, "Timestamp is 0");
        
        StudyConsent consent = studyConsentDao.getConsent(subpop.getGuid(), timestamp);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        // Only if we can publish the document, do we mark it as published in the database.
        String documentContent = loadDocumentContent(consent);
        try {
            publishFormatsToS3(study, subpop.getGuid(), documentContent);
            
            subpop.setPublishedConsentCreatedOn(timestamp);
            subpopService.updateSubpopulation(study, subpop);

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
    
    private void publishFormatsToS3(Study study, SubpopulationGuid subpopGuid, String bodyTemplate) throws DocumentException, IOException {
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
        
        String key = subpopGuid.getGuid()+"/consent.html";
        byte[] bytes = resolvedHTML.getBytes(Charset.forName(("UTF-8")));
        writeBytesToPublicS3(PUBLICATIONS_BUCKET, key, bytes, MimeType.HTML);
        
        // Now create and post a PDF version !
        try (ByteArrayBuilder buffer = new ByteArrayBuilder();) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(resolvedHTML);
            renderer.layout();
            renderer.createPDF(buffer);
            buffer.flush();
            
            key = subpopGuid.getGuid()+"/consent.pdf";
            writeBytesToPublicS3(PUBLICATIONS_BUCKET, key, buffer.toByteArray(), MimeType.PDF);
        }
    }

    /**
     * Write the byte array to a bucket at S3. The bucket will be given world read privileges, and the request
     * will be returned with the appropriate content type header for the document's MimeType.
     * @param bucket
     * @param key
     * @param data
     * @param type
     * @throws IOException
     */
    private void writeBytesToPublicS3(@Nonnull String bucket, @Nonnull String key, @Nonnull byte[] data,
            @Nonnull MimeType type) throws IOException {
        try (InputStream dataInputStream = new ByteArrayInputStream(data)) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(type.toString());
            PutObjectRequest request = new PutObjectRequest(bucket, key, dataInputStream, metadata)
                    .withCannedAcl(CannedAccessControlList.PublicRead);
            s3Client.putObject(request);
        }
    }
}