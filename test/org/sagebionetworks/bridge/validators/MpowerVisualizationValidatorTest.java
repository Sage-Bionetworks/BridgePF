package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;

import com.fasterxml.jackson.databind.node.NullNode;
import org.junit.Test;
import org.springframework.validation.MapBindingResult;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoMpowerVisualization;
import org.sagebionetworks.bridge.dynamodb.DynamoMpowerVisualizationTest;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.visualization.MpowerVisualization;

public class MpowerVisualizationValidatorTest {
    // branch coverage
    @Test
    public void supportsClass() {
        assertTrue(MpowerVisualizationValidator.INSTANCE.supports(MpowerVisualization.class));
    }

    // branch coverage
    @Test
    public void supportsSubclass() {
        assertTrue(MpowerVisualizationValidator.INSTANCE.supports(DynamoMpowerVisualization.class));
    }

    // branch coverage
    @Test
    public void doesntSupportsWrongClass() {
        assertFalse(MpowerVisualizationValidator.INSTANCE.supports(String.class));
    }

    // branch coverage
    // we call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types
    @Test
    public void nullValue() {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "MpowerVisualization");
        MpowerVisualizationValidator.INSTANCE.validate(null, errors);
        assertTrue(errors.hasErrors());
    }

    // branch coverage
    // we call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types
    @Test
    public void wrongClass() {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "MpowerVisualization");
        MpowerVisualizationValidator.INSTANCE.validate("this is the wrong class", errors);
        assertTrue(errors.hasErrors());
    }

    @Test
    public void validCase() {
        Validate.entityThrowingException(MpowerVisualizationValidator.INSTANCE,
                DynamoMpowerVisualizationTest.makeValidMpowerVisualization());
    }

    @Test
    public void invalidCase() {
        try {
            Validate.entityThrowingException(MpowerVisualizationValidator.INSTANCE, new DynamoMpowerVisualization());
            fail("expected exception");
        } catch (InvalidEntityException ex) {
            TestUtils.assertValidatorMessage(ex, "date", " must be specified");
            TestUtils.assertValidatorMessage(ex, "healthCode", " must be specified");
            TestUtils.assertValidatorMessage(ex, "visualization", " must be specified");
        }
    }

    @Test
    public void emptyHealthCode() {
        MpowerVisualization viz = DynamoMpowerVisualizationTest.makeValidMpowerVisualization();
        viz.setHealthCode("");

        try {
            Validate.entityThrowingException(MpowerVisualizationValidator.INSTANCE, viz);
            fail("expected exception");
        } catch (InvalidEntityException ex) {
            TestUtils.assertValidatorMessage(ex, "healthCode", " must be specified");
        }
    }

    @Test
    public void blankHealthCode() {
        MpowerVisualization viz = DynamoMpowerVisualizationTest.makeValidMpowerVisualization();
        viz.setHealthCode("   ");

        try {
            Validate.entityThrowingException(MpowerVisualizationValidator.INSTANCE, viz);
            fail("expected exception");
        } catch (InvalidEntityException ex) {
            TestUtils.assertValidatorMessage(ex, "healthCode", " must be specified");
        }
    }

    @Test
    public void vizJsonNull() {
        MpowerVisualization viz = DynamoMpowerVisualizationTest.makeValidMpowerVisualization();
        viz.setVisualization(NullNode.instance);

        try {
            Validate.entityThrowingException(MpowerVisualizationValidator.INSTANCE, viz);
            fail("expected exception");
        } catch (InvalidEntityException ex) {
            TestUtils.assertValidatorMessage(ex, "visualization", " must be specified");
        }
    }
}
