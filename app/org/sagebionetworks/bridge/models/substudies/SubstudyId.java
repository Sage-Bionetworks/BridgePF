package org.sagebionetworks.bridge.models.substudies;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@SuppressWarnings("serial")
@Embeddable
public final class SubstudyId implements Serializable {
    
    @Column(name = "studyId")
    private String studyId;

    @Column(name = "id")
    private String id;

    public SubstudyId() {
    }
 
    public SubstudyId(String studyId, String id) {
        this.studyId = studyId;
        this.id = id;
    }
    
    public String getStudyId() {
        return studyId;
    }
    
    public String getId() {
        return id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(studyId, id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        SubstudyId other = (SubstudyId) obj;
        return Objects.equals(studyId, other.studyId) && Objects.equals(id, other.id);
    }
}
