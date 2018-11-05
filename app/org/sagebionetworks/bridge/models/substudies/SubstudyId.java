package org.sagebionetworks.bridge.models.substudies;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public class SubstudyId implements Serializable {
    private static final long serialVersionUID = 3414483917399974708L;

    @Column(name = "id")
    private String id;
 
    @Column(name = "studyId")
    private String studyId;
    
    public SubstudyId() {
    }
 
    public SubstudyId(String id, String studyId) {
        this.id = id;
        this.studyId = studyId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, studyId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        SubstudyId other = (SubstudyId) obj;
        return Objects.equals(id, other.id) && Objects.equals(studyId, other.studyId);
    }
}
