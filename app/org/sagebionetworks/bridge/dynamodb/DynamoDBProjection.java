package org.sagebionetworks.bridge.dynamodb;

import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.amazonaws.services.dynamodbv2.model.ProjectionType;

/**
 * An optional annotation for global indices, sets the projection type of 
 * the index. This annotation should go on the same method as the annotation 
 * for the index's hash key.
 */
@Target({METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface DynamoDBProjection {
    ProjectionType projectionType();
    String globalSecondaryIndexName();
}
