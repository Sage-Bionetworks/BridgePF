package org.sagebionetworks.bridge.hibernate;

import java.util.Objects;
import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Embeddable;

/**
 * Consent key encapsulating subpopulation GUID and signedOn. This is part of the primary key of the Consents table.
 * This class does not include account ID as Hibernate embeds this object directly into HibernateAccount.
 */
// Note that Hibernate requires that this class be mutable and accesses the member variables directly. However,
// Hibernate also expects this to be used as a map key. Mutability and hashability are generally at odds with each
// other. To minimize potential problems, there are no setters on the member variables. Use with care.
@Access(AccessType.FIELD)
@Embeddable
public final class HibernateAccountConsentKey {
    private String subpopulationGuid;
    private long signedOn;

    /** No arg constructor, required by Hibernate. */
    @SuppressWarnings("unused")
    public HibernateAccountConsentKey() {
    }

    /** Constructs the consent key. */
    public HibernateAccountConsentKey(String subpopulationGuid, long signedOn) {
        this.subpopulationGuid = subpopulationGuid;
        this.signedOn = signedOn;
    }

    /** GUID that uniquely identifies the subpopulation this consent is attached to. */
    public String getSubpopulationGuid() {
        return subpopulationGuid;
    }

    /** Epoch milliseconds that this consent was signed by the user. */
    public long getSignedOn() {
        return signedOn;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HibernateAccountConsentKey)) {
            return false;
        }
        HibernateAccountConsentKey that = (HibernateAccountConsentKey) o;
        return signedOn == that.signedOn &&
                Objects.equals(subpopulationGuid, that.subpopulationGuid);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(subpopulationGuid, signedOn);
    }
}
