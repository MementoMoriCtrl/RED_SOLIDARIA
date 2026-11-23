package pe.redsolidaria.test;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class LocalRedSolidariaMySqlCondition implements ExecutionCondition {
    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        // The legacy annotation is retained, but cannot enable a test against the main database.
        return new ControlledTestMySqlCondition().evaluateExecutionCondition(context);
    }
}
