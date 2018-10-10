package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.s3.AmazonS3Client;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

public class StudyConsentServiceMockTest {
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("my-subpop");

    private StudyConsentService svc;
    private StudyConsentDao mockDao;
    private AmazonS3Client mockS3Client;

    @Before
    public void before() {
        mockDao = mock(StudyConsentDao.class);
        mockS3Client = mock(AmazonS3Client.class);

        svc = new StudyConsentService();
        svc.setStudyConsentDao(mockDao);
        svc.setS3Client(mockS3Client);
    }

    @Test
    public void deleteAll() {
        // Mock dao. We only care about the storage path.
        StudyConsent consent1 = StudyConsent.create();
        consent1.setStoragePath("storagePath1");

        StudyConsent consent2 = StudyConsent.create();
        consent2.setStoragePath("storagePath2");

        when(mockDao.getConsents(SUBPOP_GUID)).thenReturn(ImmutableList.of(consent1, consent2));

        // Execute and validate.
        svc.deleteAllConsentsPermanently(SUBPOP_GUID);

        verify(mockDao).deleteConsentPermanently(consent1);
        verify(mockDao).deleteConsentPermanently(consent2);

        verify(mockS3Client).deleteObject(StudyConsentService.CONSENTS_BUCKET, "storagePath1");
        verify(mockS3Client).deleteObject(StudyConsentService.CONSENTS_BUCKET, "storagePath2");

        verify(mockS3Client).deleteObject(StudyConsentService.PUBLICATIONS_BUCKET, "my-subpop/consent.html");
        verify(mockS3Client).deleteObject(StudyConsentService.PUBLICATIONS_BUCKET, "my-subpop/consent.pdf");
    }
}
