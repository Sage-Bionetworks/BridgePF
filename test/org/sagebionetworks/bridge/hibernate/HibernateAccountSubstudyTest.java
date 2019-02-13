package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoExternalIdentifier;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class HibernateAccountSubstudyTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(DynamoExternalIdentifier.class).allFieldsShouldBeUsed()
            .suppress(Warning.NONFINAL_FIELDS).verify();
    }
    
    @Test
    public void test() {
        HibernateAccountSubstudy accountSubstudy = new HibernateAccountSubstudy("studyId", "substudyId", "accountId");
        
        // not yet used, but coming very shortly
        accountSubstudy.setExternalId("externalId");
        
        assertEquals("studyId", accountSubstudy.getStudyId());
        assertEquals("substudyId", accountSubstudy.getSubstudyId());
        assertEquals("accountId", accountSubstudy.getAccountId());
        assertEquals("externalId", accountSubstudy.getExternalId());
        
        accountSubstudy.setSubstudyId("newSubstudyId");
        assertEquals("newSubstudyId", accountSubstudy.getSubstudyId());
    }
}
