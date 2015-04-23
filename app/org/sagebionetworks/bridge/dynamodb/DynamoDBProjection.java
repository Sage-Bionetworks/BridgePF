package org.sagebionetworks.bridge.dynamodb;

import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * For global indices, sets the projection type of the index. For range only indices, put 
 * this annotation on the method holding the range annotation, for hash/range indices, put 
 * this annotation on the method holding the hash key annotation.
 */
import com.amazonaws.services.dynamodbv2.model.ProjectionType;

@Target({METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface DynamoDBProjection {
    ProjectionType projectionType();
    String globalSecondaryIndexName();
}
