package org.sagebionetworks.bridge.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.CompoundActivity;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.schedules.TaskReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

/**
 * Helper class that converts scheduled activities so that all surveys, schemas, and compound activities 
 * reference a recent and published version that the client should be using. 
 */
class ModelReferenceResolver {
    private static final Logger LOG = LoggerFactory.getLogger(ModelReferenceResolver.class);
    
    private final Map<String, CompoundActivity> compoundActivityCache;
    private final Map<String, SchemaReference> schemaCache;
    private final Map<String, SurveyReference> surveyCache;
    private Map<String, SchemaReference> schemaReferences;
    private Map<String, SurveyReference> surveyReferences;

    ModelReferenceResolver(AppConfig appConfig) {
        this.compoundActivityCache = new HashMap<>();
        this.schemaCache = new HashMap<>();
        this.surveyCache = new HashMap<>();
        this.surveyReferences = Maps.uniqueIndex(appConfig.getSurveyReferences(), SurveyReference::getGuid);
        this.schemaReferences = Maps.uniqueIndex(appConfig.getSchemaReferences(), SchemaReference::getId);
    }
    
    // Allows for verification via a spy
    SurveyReference getSurveyReference(String guid) {
        return surveyReferences.get(guid);
    }
    
    // Allows for verification via a spy
    SchemaReference getSchemaReference(String schemaId) {
        return schemaReferences.get(schemaId);
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
                resolvedActivity = new Activity.Builder().withActivity(activity).withSurvey(resolvedSurveyRef)
                        .build();
            }
        } else if (activityType == ActivityType.TASK) {
            TaskReference taskRef = activity.getTask();
            SchemaReference schemaRef = taskRef.getSchema();
            // note: the editor doesn't allow you to set this right now.
            if (schemaRef != null) {
                SchemaReference resolvedSchemaRef = resolveSchema(schemaRef);

                if (resolvedSchemaRef != null && !resolvedSchemaRef.equals(schemaRef)) {
                    TaskReference resolvedTaskRef = new TaskReference(taskRef.getIdentifier(), resolvedSchemaRef);
                    resolvedActivity = new Activity.Builder().withActivity(activity).withTask(resolvedTaskRef)
                            .build();
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
            // Pure "reference" compound activities are fully loaded before the references are resolved by the 
            // model reference resolver. Before we cache it, resolve the surveys and schemas in the list.
            resolvedCompoundActivity = resolveListsInCompoundActivity(compoundActivity);
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

    // Helper method to resolve a published survey to a specific survey version.
    private SurveyReference resolveSurvey(SurveyReference surveyRef) {
        if (surveyRef.getCreatedOn() != null) {
            // Already has a createdOn timestamp. No need to resolve. Return as is.
            return surveyRef;
        }

        String surveyGuid = surveyRef.getGuid();
        SurveyReference resolvedSurveyRef = surveyCache.get(surveyGuid);
        if (resolvedSurveyRef == null) {
            resolvedSurveyRef = getSurveyReference(surveyGuid);
            if (resolvedSurveyRef == null) {
                LOG.error("Schedule references non-existent survey " + surveyGuid);
                return null;
            }
            surveyCache.put(surveyGuid, resolvedSurveyRef);
        }
        return resolvedSurveyRef;
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
            resolvedSchemaRef = getSchemaReference(schemaId);
            if (resolvedSchemaRef == null) {
                LOG.error("Schedule references non-existent schema " + schemaId);
                return null;
            }
            schemaCache.put(schemaId, resolvedSchemaRef);
        }
        return resolvedSchemaRef;
    }
    
}
