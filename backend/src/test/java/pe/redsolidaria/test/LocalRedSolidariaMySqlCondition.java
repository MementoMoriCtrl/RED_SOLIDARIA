package pe.redsolidaria.test;

import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class LocalRedSolidariaMySqlCondition implements ExecutionCondition {
    private static final Pattern URL = Pattern.compile(
            "^jdbc:mysql://(?:localhost|127\\.0\\.0\\.1|\\[::1\\])(?::[0-9]{1,5})?/red_solidaria(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        String username = System.getenv("DB_USERNAME");
        String url = System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/red_solidaria");
        boolean localTarget = URL.matcher(url).matches() && !hasCredentialParameter(url);
        if (username != null && !username.isBlank() && localTarget) {
            return ConditionEvaluationResult.enabled("Local red_solidaria MySQL credentials are configured");
        }
        return ConditionEvaluationResult.disabled("Requires DB_USERNAME and DB_URL targeting local red_solidaria; environment values are not displayed");
    }

    private static boolean hasCredentialParameter(String url) {
        int start = url.indexOf('?');
        if (start < 0) return false;
        for (String parameter : url.substring(start + 1).split("&")) {
            String key = parameter.split("=", 2)[0].toLowerCase(Locale.ROOT);
            if (key.equals("user") || key.equals("password") || key.equals("pwd")) return true;
        }
        return false;
    }
}
