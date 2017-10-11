package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.UploadValidationStrictness;
import org.sagebionetworks.bridge.services.StudyService;

public class StrictValidationHandlerGetValidationStrictnessTest {
    private StrictValidationHandler handler;
    private StudyService mockStudyService;

    @Before
    public void setup() {
        mockStudyService = mock(StudyService.class);
        handler = new StrictValidationHandler();
        handler.setStudyService(mockStudyService);
    }

    @Test
    public void enumStrict() {
        // mock study
        Study study = Study.create();
        study.setUploadValidationStrictness(UploadValidationStrictness.STRICT);
        study.setStrictUploadValidationEnabled(false);
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(study);

        // execute and validate
        UploadValidationStrictness retVal = handler.getUploadValidationStrictnessForStudy(TestConstants.TEST_STUDY);
        assertEquals(UploadValidationStrictness.STRICT, retVal);
    }

    @Test
    public void enumReport() {
        // mock study
        Study study = Study.create();
        study.setUploadValidationStrictness(UploadValidationStrictness.REPORT);
        study.setStrictUploadValidationEnabled(false);
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(study);

        // execute and validate
        UploadValidationStrictness retVal = handler.getUploadValidationStrictnessForStudy(TestConstants.TEST_STUDY);
        assertEquals(UploadValidationStrictness.REPORT, retVal);
    }

    @Test
    public void enumWarn() {
        // mock study
        Study study = Study.create();
        study.setUploadValidationStrictness(UploadValidationStrictness.WARNING);
        study.setStrictUploadValidationEnabled(true);
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(study);

        // execute and validate
        UploadValidationStrictness retVal = handler.getUploadValidationStrictnessForStudy(TestConstants.TEST_STUDY);
        assertEquals(UploadValidationStrictness.WARNING, retVal);
    }

    @Test
    public void booleanTrue() {
        // mock study
        Study study = Study.create();
        study.setUploadValidationStrictness(null);
        study.setStrictUploadValidationEnabled(true);
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(study);

        // execute and validate
        UploadValidationStrictness retVal = handler.getUploadValidationStrictnessForStudy(TestConstants.TEST_STUDY);
        assertEquals(UploadValidationStrictness.STRICT, retVal);
    }

    @Test
    public void booleanFalse() {
        // mock study
        Study study = Study.create();
        study.setUploadValidationStrictness(null);
        study.setStrictUploadValidationEnabled(false);
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(study);

        // execute and validate
        UploadValidationStrictness retVal = handler.getUploadValidationStrictnessForStudy(TestConstants.TEST_STUDY);
        assertEquals(UploadValidationStrictness.WARNING, retVal);
    }
}
