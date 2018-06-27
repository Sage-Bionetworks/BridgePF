package org.sagebionetworks.bridge.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.CompoundActivity;
import org.sagebionetworks.bridge.models.schedules.CompoundActivityDefinition;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.schedules.TaskReference;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Activity references can be "abstract" in the sense that they need not specify the version of a survey 
 * or schema that the client should use. When scheduled activities are generated, we "resolve" these 
 * references to the most recent schema (that matches the app version of the requestor), or the most 
 * recently published survey. In compound activities we resolve all references in the schema and survey 
 * lists. Finally, if a compound activity only contains a taskIdentifier, we load the full compound 
 * activity, resolve it, and return that in the scheduled activity. 
 */
class ReferenceResolver {
    private static final Logger LOG = LoggerFactory.getLogger(ReferenceResolver.class);
    
    private final CompoundActivityDefinitionService compoundActivityDefinitionService;
    private final UploadSchemaService schemaService;
    private final SurveyService surveyService;
    private final ClientInfo clientInfo;
    private final StudyIdentifier studyId;
    
    private final Map<String,SurveyReference> surveyReferences;
    private final Map<String,SchemaReference> schemaReferences;
    
    private final Map<String, CompoundActivity> compoundActivityCache = new HashMap<>();
    private final Map<String, SchemaReference> schemaCache = new HashMap<>();
    private final Map<String, SurveyReference> surveyCache = new HashMap<>();
    
    ReferenceResolver(CompoundActivityDefinitionService compoundActivityDefinitionService,
            UploadSchemaService schemaService, SurveyService surveyService,
            Map<String, SurveyReference> surveyReferences, Map<String, SchemaReference> schemaReferences,
            ClientInfo clientInfo, StudyIdentifier studyId) {
        this.compoundActivityDefinitionService = compoundActivityDefinitionService;
        this.schemaService = schemaService;
        this.surveyService = surveyService;
        this.surveyReferences = surveyReferences;
        this.schemaReferences = schemaReferences;
        this.clientInfo = clientInfo;
        this.studyId = studyId;
    }

    void resolve(ScheduledActivity schActivity) {
        Activity activity = schActivity.getActivity();
        ActivityType activityType = activity.getActivityType();

        // Multiplex on activity type and resolve the activity, as needed.
        Activity resolvedActivity = null;
        if (activityType == ActivityType.COMPOUND) {
            // Resolve compound activity.
            CompoundActivity compoundActivity = activity.getCompoundActivity();
            CompoundActivity resolvedCompoundActivity = resolveCompoundActivity(compoundActivity);

            // If resolution changed the compound activity, generate a new activity instance that contains it.
            if (resolvedCompoundActivity != null && !resolvedCompoundActivity.equals(compoundActivity)) {
                resolvedActivity = new Activity.Builder().withActivity(activity)
                        .withCompoundActivity(resolvedCompoundActivity).build();
            }
        } else if (activityType == ActivityType.SURVEY) {
            SurveyReference surveyRef = activity.getSurvey();
            SurveyReference resolvedSurveyRef = resolveSurvey(surveyRef);

            if (resolvedSurveyRef != null && !resolvedSurveyRef.equals(surveyRef)) {
                resolvedActivity = new Activity.Builder().withActivity(activity).withSurvey(resolvedSurveyRef).build();
            }
        } else if (activityType == ActivityType.TASK) {
            TaskReference taskRef = activity.getTask();
            SchemaReference schemaRef = taskRef.getSchema();

            if (schemaRef != null) {
                SchemaReference resolvedSchemaRef = resolveSchema(schemaRef);

                if (resolvedSchemaRef != null && !resolvedSchemaRef.equals(schemaRef)) {
                    TaskReference resolvedTaskRef = new TaskReference(taskRef.getIdentifier(), resolvedSchemaRef);
                    resolvedActivity = new Activity.Builder().withActivity(activity).withTask(resolvedTaskRef).build();
                }
            }
        }

        // Set the activity back into the ScheduledActivity, if needed.
        if (resolvedActivity != null) {
            schActivity.setActivity(resolvedActivity);
        }
    }
    // Helper method to resolve a compound activity reference from its definition.
    private CompoundActivity resolveCompoundActivity(CompoundActivity compoundActivity) {
        String taskId = compoundActivity.getTaskIdentifier();
        
        CompoundActivity resolvedCompoundActivity = compoundActivityCache.get(taskId);
        if (resolvedCompoundActivity == null) {
            if (compoundActivity.isReference()) {
                // Compound activity has no schemas or surveys defined. Resolve it with its definition.
                CompoundActivityDefinition compoundActivityDef;
                try {
                    compoundActivityDef = compoundActivityDefinitionService.getCompoundActivityDefinition(studyId,
                            taskId);
                } catch (EntityNotFoundException ex) {
                    LOG.error("Schedule references non-existent compound activity " + taskId);
                    return null;
                }
                resolvedCompoundActivity = compoundActivityDef.getCompoundActivity();
            } else {
                // Compound activity has schemas and surveys defined. Use the schemas and surveys from the lists, but
                // we may need to resolve individual schema and survey refs at a later step.
                resolvedCompoundActivity = compoundActivity;
            }

            // Before we cache it, resolve the surveys and schemas in the list.
            resolvedCompoundActivity = resolveListsInCompoundActivity(resolvedCompoundActivity);

            compoundActivityCache.put(taskId, resolvedCompoundActivity);
        }
        return resolvedCompoundActivity;
    }

    // Helper method to resolve schema refs and survey refs inside of a compound activity.
    private CompoundActivity resolveListsInCompoundActivity(CompoundActivity compoundActivity) {
        // Resolve schemas.
        // Lists in CompoundActivity are always non-null, so we don't need to null-check.
        List<SchemaReference> schemaList = new ArrayList<>();
        for (SchemaReference oneSchemaRef : compoundActivity.getSchemaList()) {
            SchemaReference resolvedSchemaRef = resolveSchema(oneSchemaRef);

            if (resolvedSchemaRef != null) {
                schemaList.add(resolvedSchemaRef);
            }
        }

        // Similarly, resolve surveys.
        List<SurveyReference> surveyList = new ArrayList<>();
        for (SurveyReference oneSurveyRef : compoundActivity.getSurveyList()) {
            SurveyReference resolvedSurveyRef = resolveSurvey(oneSurveyRef);

            if (resolvedSurveyRef != null) {
                surveyList.add(resolvedSurveyRef);
            }
        }

        // Make a new compound activity with the resolved schemas and surveys. This is cached in
        // resolveCompoundActivities(), so this is okay.
        return new CompoundActivity.Builder().copyOf(compoundActivity).withSchemaList(schemaList)
                .withSurveyList(surveyList).build();
    }

    // Helper method to resolve a schema ref to the latest revision for the client.
    private SchemaReference resolveSchema(SchemaReference schemaRef) {
        if (schemaRef.getRevision() != null) {
            // Already has a revision. No need to resolve. Return as is.
            return schemaRef;
        }

        String schemaId = schemaRef.getId();
        SchemaReference resolvedSchemaRef = schemaCache.get(schemaId);
        if (resolvedSchemaRef == null) {
            resolvedSchemaRef = schemaReferences.get(schemaId);
        }
        if (resolvedSchemaRef == null) {
            UploadSchema schema;
            try {
                schema = schemaService.getLatestUploadSchemaRevisionForAppVersion(studyId, schemaId, clientInfo);
            } catch (EntityNotFoundException ex) {
                LOG.error("Schedule references non-existent schema " + schemaId);
                return null;
            }
            resolvedSchemaRef = new SchemaReference(schemaId, schema.getRevision());
            schemaCache.put(schemaId, resolvedSchemaRef);
        }
        return resolvedSchemaRef;
    }

    // Helper method to resolve a published survey to a specific survey version.
    private SurveyReference resolveSurvey(SurveyReference surveyRef) {
        if (surveyRef.getCreatedOn() != null && surveyRef.getIdentifier() != null) {
            return surveyRef;
        }

        String surveyGuid = surveyRef.getGuid();
        SurveyReference resolvedSurveyRef = surveyCache.get(surveyGuid);
        if (resolvedSurveyRef == null) {
            resolvedSurveyRef = surveyReferences.get(surveyGuid);
        }
        if (resolvedSurveyRef == null) {
            Survey survey;
            try {
                survey = surveyService.getSurveyMostRecentlyPublishedVersion(studyId, surveyGuid, false);
            } catch (EntityNotFoundException ex) {
                LOG.error("Schedule references non-existent survey " + surveyGuid);
                return null;
            }
            resolvedSurveyRef = new SurveyReference(survey.getIdentifier(), surveyGuid,
                    new DateTime(survey.getCreatedOn()));
            surveyCache.put(surveyGuid, resolvedSurveyRef);
        }
        return resolvedSurveyRef;
    }
    
}
