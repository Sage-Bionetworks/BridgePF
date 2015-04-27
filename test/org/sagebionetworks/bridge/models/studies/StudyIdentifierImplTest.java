package org.sagebionetworks.bridge.models.studies;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.junit.Test;

public class StudyIdentifierImplTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(StudyIdentifierImpl.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
    
}
