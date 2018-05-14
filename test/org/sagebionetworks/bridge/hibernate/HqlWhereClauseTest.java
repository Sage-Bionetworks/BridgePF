package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

public class HqlWhereClauseTest {
    
    HqlWhereClause clause;
    
    @Before
    public void before() {
        clause = new HqlWhereClause(false);
    }
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(HqlWhereClause.class).allFieldsShouldBeUsed().verify();
        
        // I tried using an ImmutableMap.Builder at one point, and it broke equality although
        // EqualsVerifier was passing.
        assertEquals(new HqlWhereClause(false), new HqlWhereClause(false));
    }
    
    @Test
    public void concatenatesExpressions() {
        clause.addExpression("boolean=true");
        clause.addExpression("notes=:notes", "notes");
        assertEquals("boolean=true AND notes=:notes", clause.getClause());
        
        clause = new HqlWhereClause(true);
        clause.addExpression("boolean=true");
        clause.addExpression("notes=:notes", "notes");
        assertEquals("boolean=true OR notes=:notes", clause.getClause());
    }
    
    @Test
    public void acceptsParameters() {
        clause.addExpression("one=:one", "string");
        clause.addExpression("two=:two", 2);
        clause.addExpression("three=:three", 3L);
        clause.addExpression("four=:four", true);
        clause.addExpression("five=:five", ImmutableList.of(1,2,3));
        
        assertEquals(5, clause.getParameters().size());
        assertEquals("string", clause.getParameters().get("one"));
        assertEquals(new Integer(2), (Integer)clause.getParameters().get("two"));
        assertEquals(new Long(3), (Long)clause.getParameters().get("three"));
        assertEquals(true, (Boolean)clause.getParameters().get("four"));
        assertEquals(ImmutableList.of(1,2,3), (List<?>)clause.getParameters().get("five"));
    }
    
    @Test
    public void transformsLikeParameters() {
        clause.addExpression("one like :one", "notes");
        assertEquals("%notes%", clause.getParameters().get("one"));
    }
    
    @Test
    public void ignoresNulls() {
        clause.addExpression("boolean=true", null);
        assertNull(clause.getClause());
        assertTrue(clause.getParameters().isEmpty());
    }
}
