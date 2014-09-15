package org.sagebionetworks.bridge.validators;

import java.util.LinkedList;

import com.google.common.base.Joiner;

/**
 * Does not store messages for individual fields, which is very likely
 * something that we'd want.
 */
public class Messages extends LinkedList<String> {
    private static final long serialVersionUID = -5357208558545390989L;
    
    public void add(String message, Object... arguments) {
        super.add(String.format(message, arguments));
    }
    
    public String join() {
        return Joiner.on("; ").join(this);
    }
}
