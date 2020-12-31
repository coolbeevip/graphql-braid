package com.atlassian.braid.mapper;

import com.atlassian.braid.java.util.BraidObjects;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.util.Map;
import java.util.Optional;

import static org.springframework.expression.spel.SpelMessage.PROPERTY_OR_FIELD_NOT_READABLE;

final class SpringExpressions {
    private static final org.springframework.expression.ExpressionParser PARSER = new SpelExpressionParser();

    // not static on purpose
    <T> Optional<T> get(Map<String, Object> map, String key) {
        final String sourcePath = !key.contains("[") ? "['" + key + "']" : key;
        return Optional.ofNullable(maybeGetValue(map, sourcePath)).map(BraidObjects::cast);
    }

    private static Object maybeGetValue(Object source, String sourcePath) {
        try {
            return PARSER.parseExpression(sourcePath).getValue(source);
        } catch (SpelEvaluationException e) {
            // PROPERTY_OR_FIELD_NOT_READABLE is thrown when an intermediate property doesn't exist when getting value
            // for a leaf node. Instead of throwing an exception, we should simply return null for the leaf node.
            if (e.getMessageCode() == PROPERTY_OR_FIELD_NOT_READABLE) {
                return null;
            }

            throw new MapperException(e, "Exception getting value in %s for path: %s", source, sourcePath);
        }
    }
}
