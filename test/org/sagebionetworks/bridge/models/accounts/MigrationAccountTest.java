package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.stormpath.StormpathAccount;

public class MigrationAccountTest {
    private static final String ATTR_NAME = "my-attr";
    private static final List<ConsentSignature> BOTH_CONSENT_LIST = ImmutableList.of(new ConsentSignature.Builder()
            .withName("both-first-name both-last-name").withBirthdate("1993-03-03").withSignedOn(3333).build());
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("my-subpop");

    private static final String BOTH_HEALTH_CODE = "both-health-code";
    private static final String BOTH_HEALTH_ID_STRING = "both-health-id";
    private static final HealthId BOTH_HEALTH_ID = new HealthIdImpl(BOTH_HEALTH_ID_STRING, BOTH_HEALTH_CODE);

    private GenericAccount genericAccount;
    private StormpathAccount mockStormpathAccount;

    @Before
    public void setup() {
        // Set up GenericAccount and mock StormpathAccount. The two accounts will have slightly different data to test
        // fallback logic, even though this is not particularly realistic.

        // GenericAccount
        genericAccount = new GenericAccount();
        genericAccount.setFirstName("Bobby");
        genericAccount.setLastName("Generic");
        genericAccount.setAttribute(ATTR_NAME, "generic-attr-value");
        genericAccount.setEmail("generic@example.com");
        genericAccount.setHealthCode("generic-health-code");
        genericAccount.setStatus(AccountStatus.UNVERIFIED);
        genericAccount.setStudyId(new StudyIdentifierImpl("generic-study"));
        genericAccount.setRoles(EnumSet.of(Roles.TEST_USERS));
        genericAccount.setId("generic-account-id");
        genericAccount.setCreatedOn(new DateTime(1111));

        // generic consents
        List<ConsentSignature> genericConsentList = ImmutableList.of(new ConsentSignature.Builder()
                .withName("Bobby Generic").withBirthdate("1991-01-01").withSignedOn(1111).build());
        genericAccount.setConsentSignatureHistory(SUBPOP_GUID, genericConsentList);

        // StormpathAccount
        mockStormpathAccount = mock(StormpathAccount.class);
        when(mockStormpathAccount.getFirstName()).thenReturn("Eggplant");
        when(mockStormpathAccount.getLastName()).thenReturn("McTester");
        when(mockStormpathAccount.getAttribute(ATTR_NAME)).thenReturn("stormpath-attr-value");
        when(mockStormpathAccount.getEmail()).thenReturn("stormpath@example.com");
        when(mockStormpathAccount.getHealthCode()).thenReturn("stormpath-health-code");
        when(mockStormpathAccount.getStatus()).thenReturn(AccountStatus.ENABLED);
        when(mockStormpathAccount.getStudyIdentifier()).thenReturn(new StudyIdentifierImpl("stormpath-study"));
        when(mockStormpathAccount.getRoles()).thenReturn(EnumSet.of(Roles.ADMIN));
        when(mockStormpathAccount.getId()).thenReturn("stormpath-account-id");
        when(mockStormpathAccount.getCreatedOn()).thenReturn(new DateTime(2222));

        // stormpath consents
        List<ConsentSignature> stormpathConsentList = ImmutableList.of(new ConsentSignature.Builder()
                .withName("Eggplant McTester").withBirthdate("1992-02-02").withSignedOn(2222).build());
        when(mockStormpathAccount.getConsentSignatureHistory(SUBPOP_GUID)).thenReturn(stormpathConsentList);
        Map<SubpopulationGuid, List<ConsentSignature>> stormpathConsentMap = ImmutableMap
                .<SubpopulationGuid, List<ConsentSignature>>builder().put(SUBPOP_GUID, stormpathConsentList).build();
        when(mockStormpathAccount.getAllConsentSignatureHistories()).thenReturn(stormpathConsentMap);
    }

    @Test
    public void both() {
        // Should read from GenericAccount, write to both.
        MigrationAccount account = new MigrationAccount(genericAccount, mockStormpathAccount);
        assertSameProperties(genericAccount, account);

        callSetters(account);
        verifyWroteToGenericAccount();
        verifyWroteToStormpathAccount();
    }

    @Test
    public void genericOnly() {
        // Should read and write from GenericAccount.
        MigrationAccount account = new MigrationAccount(genericAccount, null);
        assertSameProperties(genericAccount, account);

        callSetters(account);
        verifyWroteToGenericAccount();
    }

    @Test
    public void stormpathOnly() {
        // Should read and write from StormpathAccount;
        MigrationAccount account = new MigrationAccount(null, mockStormpathAccount);
        assertSameProperties(mockStormpathAccount, account);

        callSetters(account);
        verifyWroteToStormpathAccount();
    }

    private static void assertSameProperties(Account expected, MigrationAccount actual) {
        assertEquals(expected.getFirstName(), actual.getFirstName());
        assertEquals(expected.getLastName(), actual.getLastName());
        assertEquals(expected.getAttribute(ATTR_NAME), actual.getAttribute(ATTR_NAME));
        assertEquals(expected.getEmail(), actual.getEmail());
        assertEquals(expected.getConsentSignatureHistory(SUBPOP_GUID), actual.getConsentSignatureHistory(SUBPOP_GUID));
        assertEquals(expected.getAllConsentSignatureHistories(), actual.getAllConsentSignatureHistories());
        assertEquals(expected.getHealthCode(), actual.getHealthCode());
        assertEquals(expected.getStatus(), actual.getStatus());
        assertEquals(expected.getStudyIdentifier(), actual.getStudyIdentifier());
        assertEquals(expected.getRoles(), actual.getRoles());
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getCreatedOn(), actual.getCreatedOn());
    }

    private static void callSetters(MigrationAccount account) {
        account.setFirstName("both-first-name");
        account.setLastName("both-last-name");
        account.setAttribute(ATTR_NAME, "both-attr-value");
        account.setEmail("both@example.com");
        account.setConsentSignatureHistory(SUBPOP_GUID, BOTH_CONSENT_LIST);
        account.setHealthId(BOTH_HEALTH_ID);
        account.setStatus(AccountStatus.DISABLED);
        account.setRoles(EnumSet.of(Roles.ADMIN, Roles.TEST_USERS));
    }

    private void verifyWroteToGenericAccount() {
        assertEquals("both-first-name", genericAccount.getFirstName());
        assertEquals("both-last-name", genericAccount.getLastName());
        assertEquals("both-attr-value", genericAccount.getAttribute(ATTR_NAME));
        assertEquals("both@example.com", genericAccount.getEmail());
        assertEquals(BOTH_CONSENT_LIST, genericAccount.getConsentSignatureHistory(SUBPOP_GUID));
        assertEquals(BOTH_HEALTH_CODE, genericAccount.getHealthCode());
        assertEquals(BOTH_HEALTH_ID_STRING, genericAccount.getHealthId());
        assertEquals(AccountStatus.DISABLED, genericAccount.getStatus());
        assertEquals(EnumSet.of(Roles.ADMIN, Roles.TEST_USERS), genericAccount.getRoles());
    }

    private void verifyWroteToStormpathAccount() {
        verify(mockStormpathAccount).setFirstName("both-first-name");
        verify(mockStormpathAccount).setLastName("both-last-name");
        verify(mockStormpathAccount).setAttribute(ATTR_NAME, "both-attr-value");
        verify(mockStormpathAccount).setEmail("both@example.com");
        verify(mockStormpathAccount).setConsentSignatureHistory(SUBPOP_GUID, BOTH_CONSENT_LIST);
        verify(mockStormpathAccount).setHealthId(BOTH_HEALTH_ID);
        verify(mockStormpathAccount).setStatus(AccountStatus.DISABLED);
        verify(mockStormpathAccount).setRoles(EnumSet.of(Roles.ADMIN, Roles.TEST_USERS));
    }

    @Test
    public void neither() {
        // Should never happen, but test it for branch coverage.
        MigrationAccount account = new MigrationAccount(null, null);

        // Test reads. Everything is null, but we want to check that nothing throws an NPE.
        assertNull(account.getFirstName());
        assertNull(account.getLastName());
        assertNull(account.getAttribute(ATTR_NAME));
        assertNull(account.getEmail());
        assertNull(account.getConsentSignatureHistory(SUBPOP_GUID));
        assertNull(account.getAllConsentSignatureHistories());
        assertNull(account.getHealthCode());
        assertNull(account.getStatus());
        assertNull(account.getStudyIdentifier());
        assertNull(account.getRoles());
        assertNull(account.getId());
        assertNull(account.getCreatedOn());

        // Doesn't throw an NPE if we call setters.
        callSetters(account);
    }
}
