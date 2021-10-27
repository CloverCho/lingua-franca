/*
 * Copyright (c) 2021, TU Dresden.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.lflang;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.lflang.lf.Action;
import org.lflang.lf.Initializer;
import org.lflang.lf.ParamRef;
import org.lflang.lf.Parameter;
import org.lflang.lf.Port;
import org.lflang.lf.StateVar;
import org.lflang.lf.Time;
import org.lflang.lf.Type;
import org.lflang.lf.Value;

/**
 * Helper class to manipulate the LF AST. This is partly
 * converted from {@link ASTUtils}.
 */
public final class JavaAstUtils {
    /* Match an abbreviated form of a float literal. */
    private static final Pattern ABBREVIATED_FLOAT = Pattern.compile("[+\\-]?\\.\\d+[\\deE+\\-]*");

    private JavaAstUtils() {
        // utility class
    }

    /**
     * Return the type of a declaration with the given
     * (nullable) explicit type, and the given (nullable)
     * initializer. If the explicit type is null, then the
     * type is inferred from the initializer. Only two types
     * can be inferred: "time" and "timeList". Return the
     * "undefined" type if neither can be inferred.
     *
     * @param type Explicit type declared on the declaration
     * @param init An initializer, possibly null
     * @return The inferred type, or "undefined" if none could be inferred.
     */
    public static InferredType getInferredType(Type type, Initializer init) {
        if (type != null) {
            return InferredType.fromAST(type);
        } else if (init == null) {
            return InferredType.undefined();
        }

        var single = JavaAstUtils.asSingleValue(init);
        if (single != null) {
            // If there is a single element in the list, and it is a proper
            // time value with units, we infer the type "time".
            if (single instanceof ParamRef) {
                return getInferredType(((ParamRef) single).getParameter());
            } else if (ASTUtils.isValidTime(single) && !ASTUtils.isZero(single)) {
                return InferredType.time();
            }
        } else if (init.getExprs().size() > 1) {
            // If there are multiple elements in the list, and there is at
            // least one proper time value with units, and all other elements
            // are valid times (including zero without units), we infer the
            // type "time list".
            var allValidTime = true;
            var foundNonZero = false;

            for (var e : init.getExprs()) {
                if (!ASTUtils.isValidTime(e)) {
                    allValidTime = false;
                }
                if (!ASTUtils.isZero(e)) {
                    foundNonZero = true;
                }
            }

            if (allValidTime && foundNonZero) {
                // Conservatively, no bounds are inferred; the returned type
                // is a variable-size list.
                return InferredType.timeList();
            }
        }
        return InferredType.undefined();
    }

    /**
     * Given a parameter, return an inferred type. Only two types can be
     * inferred: "time" and "timeList". Return the "undefined" type if
     * neither can be inferred.
     *
     * @param p A parameter to infer the type of.
     * @return The inferred type, or "undefined" if none could be inferred.
     */
    public static InferredType getInferredType(Parameter p) {
        return getInferredType(p.getType(), p.getInit());
    }

    /**
     * Given a state variable, return an inferred type. Only two types can be
     * inferred: "time" and "timeList". Return the "undefined" type if
     * neither can be inferred.
     *
     * @param s A state variable to infer the type of.
     * @return The inferred type, or "undefined" if none could be inferred.
     */
    public static InferredType getInferredType(StateVar s) {
        return getInferredType(s.getType(), s.getInit());
    }

    /**
     * Construct an inferred type from an "action" AST node based
     * on its declared type. If no type is declared, return the "undefined"
     * type.
     *
     * @param a An action to construct an inferred type object for.
     * @return The inferred type, or "undefined" if none was declared.
     */
    public static InferredType getInferredType(Action a) {
        return getInferredType(a.getType(), null);
    }

    /**
     * Construct an inferred type from a "port" AST node based on its declared
     * type. If no type is declared, return the "undefined" type.
     *
     * @param p A port to construct an inferred type object for.
     * @return The inferred type, or "undefined" if none was declared.
     */
    public static InferredType getInferredType(Port p) {
        return getInferredType(p.getType(), null);
    }

    /**
     * If the initializer contains exactly one expression,
     * return it. Otherwise return null.
     */
    public static Value asSingleValue(Initializer init) {
        List<Value> exprs = init.getExprs();
        return exprs.size() == 1 ? exprs.get(0) : null;
    }

    /**
     * Returns true if the initializer represents a list value.
     */
    public static boolean isList(Initializer init) {
        return (init.isBraces() || init.isParens()) && init.getExprs().size() != 1;
        // || init.isAssign && init.asSingleValue instanceof ListExpr
    }

    /**
     * Format the initializer as it would appear in LF.
     */
    public static String toText(Initializer init) {
        if (init.isBraces()) {
            return init.getExprs().stream()
                       .map(ASTUtils::toText)
                       .collect(Collectors.joining(", ", "{", "}"));
        } else if (init.isParens()) {
            return init.getExprs().stream()
                       .map(ASTUtils::toText)
                       .collect(Collectors.joining(", ", "(", ")"));
        } else if (init.isAssign()) {
            return "= " + ASTUtils.toText(asSingleValue(init));
        } else {
            return ""; // no initializer
        }
    }

    /**
     * Returns the time value represented by the given AST node.
     */
    public static TimeValue toTimeValue(Time e) {
        return new TimeValue(e.getInterval(), e.getUnit());
    }

    /**
     * If the given string can be recognized as a floating-point number that has a leading decimal point,
     * prepend the string with a zero and return it. Otherwise, return the original string.
     * @param literal A string might be recognizable as a floating point number with a leading decimal point.
     * @return an equivalent representation of <code>literal
     * </code>
     */
    public static String addZeroToLeadingDot(String literal) {
        Matcher m = ABBREVIATED_FLOAT.matcher(literal);
        if (m.matches()) return literal.replace(".", "0.");
        return literal;
    }
}
