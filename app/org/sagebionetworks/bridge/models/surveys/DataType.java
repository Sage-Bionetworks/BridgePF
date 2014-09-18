package org.sagebionetworks.bridge.models.surveys;

public enum DataType {
    DURATION(Long.class),
    STRING(String.class),
    INTEGER(Long.class),
    DECIMAL(Double.class),
    BOOLEAN(Boolean.class),
    DATE(Long.class),
    TIME(Long.class),
    DATETIME(Long.class);
    
    private Class<?> castClass;
    
    private DataType(Class<?> castClass) {
        this.castClass = castClass;
    }
    
    public Class<?> getCastClass() {
        return castClass;
    }
}
