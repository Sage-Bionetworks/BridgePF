package org.sagebionetworks.bridge.services.backfill;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.models.backfill.BackfillTask;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.SurveyService;
import org.sagebionetworks.bridge.services.UploadSchemaService;

/** Backfill to write survey guid and createdOn into survey schemas. */
@Component("schemaSurveyGuidBackfill")
public class SchemaSurveyGuidBackfill extends AsyncBackfillTemplate {
    private static final Logger LOG = LoggerFactory.getLogger(SchemaSurveyGuidBackfill.class);

    private StudyService studyService;
    private SurveyService surveyService;
    private UploadSchemaService uploadSchemaService;

    /** Used to get all studies. */
    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    /** Used to get all surveys. */
    @Autowired
    final void setSurveyService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }

    /** Used to get and update survey schemas. */
    @Autowired
    final void setUploadSchemaService(UploadSchemaService uploadSchemaService) {
        this.uploadSchemaService = uploadSchemaService;
    }

    @Override
    int getLockExpireInSeconds() {
        // There are currently 318 entries in prod-heroku-Survey. If we handle 1 per second, that's a little over 5
        // min. For safety, set the timeout to 15 min.
        return 900;
    }

    @Override
    void doBackfill(BackfillTask task, BackfillCallback callback) {
        // We need to backfill for every study.
        List<Study> studyList = studyService.getStudies();
        for (Study oneStudy : studyList) {
            // And every published survey in every study.
            StudyIdentifier studyId = oneStudy.getStudyIdentifier();

            try {
                List<Survey> surveyList = surveyService.getAllSurveysMostRecentlyPublishedVersion(studyId, false);
                for (Survey oneSurvey : surveyList) {
                    // Sleep for 1 sec so we don't brown out DDB.
                    sleep();

                    String surveyGuid = oneSurvey.getGuid();
                    long surveyCreatedOn = oneSurvey.getCreatedOn();

                    // Schema ID (survey ID) is always present. Check schema rev
                    String schemaId = oneSurvey.getIdentifier();
                    Integer schemaRev = oneSurvey.getSchemaRevision();
                    if (schemaRev == null) {
                        recordMessage(task, callback, "Skipping survey " + surveyGuid + "/" + surveyCreatedOn +
                                ": No schema associated");
                        continue;
                    }

                    try {
                        // Get schema and check if it already has survey guid and createdOn
                        UploadSchema schema = uploadSchemaService.getUploadSchemaByIdAndRev(studyId, schemaId,
                                schemaRev);
                        if (schema.getSurveyGuid() != null && schema.getSurveyCreatedOn() != null) {
                            recordMessage(task, callback, "Skipping schema " + schemaId + " rev " + schemaRev +
                                    ": Already has survey guid and createdOn");
                            continue;
                        }

                        // Update the schema to include survey guid and createdOn.
                        schema.setSurveyGuid(surveyGuid);
                        schema.setSurveyCreatedOn(surveyCreatedOn);
                        uploadSchemaService.updateSchemaRevisionV4(studyId, schemaId, schemaRev, schema);

                        recordMessage(task, callback, "Backfilled schema " + schemaId + " rev " + schemaRev);
                    } catch (RuntimeException ex) {
                        // Ensure that errors don't fail the entire backfill.
                        recordError(task, callback, "Error backfilling schema " + schemaId + " rev " + schemaRev +
                                ": " + ex.getMessage(), ex);
                    }
                }
            } catch (RuntimeException ex) {
                // Similarly, here
                recordError(task, callback, "Error backfilling schemas for study " + studyId.getIdentifier() + ": " +
                        ex.getMessage(), ex);
            }
        }
    }

    // Helper method which sleeps for 1 second. This exists so the unit tests can mock it out and make it do nothing.
    void sleep() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            LOG.error("Interrupted while sleeping: " + ex.getMessage(), ex);
        }
    }
}
