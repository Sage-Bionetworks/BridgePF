package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.google.common.collect.ImmutableSet;

public class QueryBuilderTest {

    @Test
    public void testSimpleParams() {
        QueryBuilder builder = new QueryBuilder();
        builder.append("phrase");
        builder.append("phrase two=:two", "two", "valueForTwo");
        builder.append("phrase three=:three four=:four", "three", "valueForThree", "four", "valueForFour");

        assertEquals("phrase phrase two=:two phrase three=:three four=:four", builder.getQuery());
        assertEquals("valueForTwo", builder.getParameters().get("two"));
        assertEquals("valueForThree", builder.getParameters().get("three"));
        assertEquals("valueForFour", builder.getParameters().get("four"));
    }
    
    @Test
    public void testDataGroups() {
        QueryBuilder builder = new QueryBuilder();
        builder.dataGroups(ImmutableSet.of("A", "B"), "IN");
        builder.dataGroups(ImmutableSet.of("C", "D"), "NOT IN");
        
        assertEquals("AND (:IN1 IN elements(acct.dataGroups) AND :IN2 IN elements(acct.dataGroups)) AND " +
                "(:NOTIN1 NOT IN elements(acct.dataGroups) AND :NOTIN2 NOT IN elements(acct.dataGroups))",
                builder.getQuery());
        assertEquals("A", builder.getParameters().get("IN1"));
        assertEquals("B", builder.getParameters().get("IN2"));
        assertEquals("C", builder.getParameters().get("NOTIN1"));
        assertEquals("D", builder.getParameters().get("NOTIN2"));
    }
}
