package com.syntaric.openfhir.mapping.toaql;

import java.util.regex.Pattern;
import org.ehrbase.openehr.sdk.aql.dto.condition.ComparisonOperatorCondition;
import org.ehrbase.openehr.sdk.aql.dto.condition.WhereCondition;
import org.ehrbase.openehr.sdk.aql.dto.operand.DoublePrimitive;
import org.ehrbase.openehr.sdk.aql.dto.operand.LongPrimitive;
import org.ehrbase.openehr.sdk.aql.parser.AqlQueryParser;

/**
 * Literals for text that comes from a request (a search value, an ehrId, a subject) and goes into an AQL. Every such
 * value goes through here, so that it cannot leave its literal and change what the query selects.
 * <ul>
 *     <li>a string (text, a code, an identifier, a date or time, which are compared as text and not validated) is
 *     quoted with single quotes, with a backslash before every backslash, single quote and double quote, as the AQL
 *     grammar escapes them. openEHR SDK 2.19 and 2.35 read {@code 'O\'Brien'} as {@code O'Brien} and
 *     {@code 'a\"b'} as {@code a"b}, and {@code \'} inside a double-quoted literal as {@code '}, so the escaped text
 *     stays in either kind of literal; a doubled {@code ''} does not parse;</li>
 *     <li>a number is written as it is and has to be one the AQL parser reads as a number: digits with an optional
 *     minus, fraction and exponent ({@code 500}, {@code -1.5}, {@code .5}, {@code 1e3}, {@code 1e-3}), within the
 *     range of a long for an integer and of a double for the rest. The parser itself decides: {@code 1e+3},
 *     {@code 9223372036854775808} and {@code 1e309} are not numbers to it;</li>
 *     <li>a boolean is written as it is and has to be {@code true} or {@code false}, in any case.</li>
 * </ul>
 */
public final class AqlLiterals {

    /** The characters and shape of an AQL number; the parser decides whether it reads it. */
    private static final Pattern NUMBER = Pattern.compile("-?(\\d+|\\d*\\.\\d+)([eE][+-]?\\d+)?");
    private static final Pattern BOOLEAN = Pattern.compile("(?i)true|false");

    private AqlLiterals() {
    }

    /** {@code value} as the inside of a string literal: a backslash before every backslash and quote. */
    public static String escape(final String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"");
    }

    /** {@code value} as a string literal: in single quotes, {@link #escape escaped}. */
    public static String string(final String value) {
        return "'" + escape(value) + "'";
    }

    /** Whether {@code value} is a number the AQL parser reads as one, see the class comment. */
    public static boolean isNumber(final String value) {
        if (value == null || !NUMBER.matcher(value).matches()) {
            return false;
        }
        try {
            final WhereCondition where = AqlQueryParser.parse("SELECT e FROM EHR e WHERE e/ehr_id/value = " + value)
                    .getWhere();
            return where instanceof ComparisonOperatorCondition comparison
                    && (comparison.getValue() instanceof LongPrimitive
                    || comparison.getValue() instanceof DoublePrimitive);
        } catch (final Exception e) {
            return false;
        }
    }

    /** Whether {@code value} is {@code true} or {@code false}, in any case. */
    public static boolean isBoolean(final String value) {
        return value != null && BOOLEAN.matcher(value).matches();
    }

    /**
     * @return {@code value} as it is
     * @throws IllegalArgumentException when it is not a number the AQL parser reads as one
     */
    public static String number(final String value) {
        if (!isNumber(value)) {
            throw new IllegalArgumentException("not a number");
        }
        return value;
    }

    /**
     * @return {@code value} as it is
     * @throws IllegalArgumentException when it is not {@code true} or {@code false}
     */
    public static String bool(final String value) {
        if (!isBoolean(value)) {
            throw new IllegalArgumentException("not true or false");
        }
        return value;
    }
}
