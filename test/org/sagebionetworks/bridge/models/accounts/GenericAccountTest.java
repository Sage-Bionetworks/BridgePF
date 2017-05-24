package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

public class GenericAccountTest {
    @Test
    public void attributes() {
        GenericAccount account = new GenericAccount();

        // Setting attributes adds them to the map.
        account.setAttribute("foo", "foo-value");
        account.setAttribute("bar", "bar-value");
        assertEquals(ImmutableSet.of("foo", "bar"), account.getAttributeNameSet());
        assertEquals("foo-value", account.getAttribute("foo"));
        assertEquals("bar-value", account.getAttribute("bar"));

        // Setting attributes to null removes them from the map.
        account.setAttribute("foo", null);
        assertEquals(ImmutableSet.of("bar"), account.getAttributeNameSet());
        assertEquals("bar-value", account.getAttribute("bar"));

        // Setting attributes to null or empty string will also not add them to the map.
        account.setAttribute("foo", "");
        assertEquals(ImmutableSet.of("bar"), account.getAttributeNameSet());
        account.setAttribute("foo", "   ");
        assertEquals(ImmutableSet.of("bar"), account.getAttributeNameSet());
    }

    @Test
    public void consents() {
        GenericAccount account = new GenericAccount();
        assertTrue(account.getAllConsentSignatureHistories().isEmpty());

        // Make dummy subpops.
        SubpopulationGuid fooSubpopGuid = SubpopulationGuid.create("foo-subpop-guid");
        SubpopulationGuid barSubpopGuid = SubpopulationGuid.create("bar-subpop-guid");

        ConsentSignature fooConsentSignature = new ConsentSignature.Builder().withName("Foo McFooface")
                .withBirthdate("1999-01-01").build();
        ConsentSignature barConsentSignature = new ConsentSignature.Builder().withName("Bar McBarface")
                .withBirthdate("1999-02-02").build();

        // Getting by subpop instantiates the list
        List<ConsentSignature> fooConsentList1 = account.getConsentSignatureHistory(fooSubpopGuid);
        assertNotNull(fooConsentList1);
        assertTrue(fooConsentList1.isEmpty());

        Map<SubpopulationGuid, List<ConsentSignature>> consentsBySubpop1 = account.getAllConsentSignatureHistories();
        assertEquals(1, consentsBySubpop1.size());
        assertTrue(consentsBySubpop1.get(fooSubpopGuid).isEmpty());

        // Adding to the list writes back through
        fooConsentList1.add(fooConsentSignature);

        List<ConsentSignature> fooConsentList2 = account.getConsentSignatureHistory(fooSubpopGuid);
        assertEquals(1, fooConsentList2.size());
        assertEquals(fooConsentSignature, fooConsentList2.get(0));

        Map<SubpopulationGuid, List<ConsentSignature>> consentsBySubpop2 = account.getAllConsentSignatureHistories();
        assertEquals(1, consentsBySubpop2.size());
        assertEquals(fooConsentList2, consentsBySubpop2.get(fooSubpopGuid));

        // Getting the full map and writing to it writes back through
        List<ConsentSignature> originalBarConsentList = new ArrayList<>();
        originalBarConsentList.add(barConsentSignature);
        consentsBySubpop2.put(barSubpopGuid, originalBarConsentList);

        Map<SubpopulationGuid, List<ConsentSignature>> consentsBySubpop3 = account.getAllConsentSignatureHistories();
        assertEquals(2, consentsBySubpop3.size());

        List<ConsentSignature> fooConsentList3 = consentsBySubpop3.get(fooSubpopGuid);
        assertEquals(1, fooConsentList3.size());
        assertEquals(fooConsentSignature, fooConsentList3.get(0));

        List<ConsentSignature> gettedBarConsentList = consentsBySubpop3.get(barSubpopGuid);
        assertEquals(1, gettedBarConsentList.size());
        assertEquals(barConsentSignature, gettedBarConsentList.get(0));

        assertEquals(fooConsentList3, account.getConsentSignatureHistory(fooSubpopGuid));
        assertEquals(gettedBarConsentList, account.getConsentSignatureHistory(barSubpopGuid));
    }

    @Test
    public void healthCodeHealthId() {
        GenericAccount account = new GenericAccount();

        // Can set individually.
        account.setHealthId("dummy-health-id-1");
        assertEquals("dummy-health-id-1", account.getHealthId());

        account.setHealthCode("dummy-health-code-1");
        assertEquals("dummy-health-code-1", account.getHealthCode());

        // Can set from HealthId object.
        account.setHealthId(new HealthIdImpl("dummy-health-id-2", "dummy-health-code-2"));
        assertEquals("dummy-health-id-2", account.getHealthId());
        assertEquals("dummy-health-code-2", account.getHealthCode());
    }

    @Test
    public void roles() {
        // Initially empty.
        GenericAccount account = new GenericAccount();
        assertTrue(account.getRoles().isEmpty());

        // Set works.
        account.setRoles(EnumSet.of(Roles.ADMIN, Roles.DEVELOPER));
        assertEquals(EnumSet.of(Roles.ADMIN, Roles.DEVELOPER), account.getRoles());

        // Setting to null makes it an empty set.
        account.setRoles(null);
        assertTrue(account.getRoles().isEmpty());

        // Set to non-empty, then set to empty and verify that it works.
        account.setRoles(EnumSet.of(Roles.RESEARCHER));
        assertEquals(EnumSet.of(Roles.RESEARCHER), account.getRoles());

        account.setRoles(EnumSet.noneOf(Roles.class));
        assertTrue(account.getRoles().isEmpty());
    }
}
