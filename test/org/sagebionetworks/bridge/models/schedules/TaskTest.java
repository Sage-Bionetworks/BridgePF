package org.sagebionetworks.bridge.models.schedules;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.junit.Test;

public class TaskTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(Task.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }

}
