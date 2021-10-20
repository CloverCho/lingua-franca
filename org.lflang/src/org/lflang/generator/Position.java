package org.lflang.generator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a position in a document, including line and
 * column. This position may be relative to another
 * position other than the origin.
 */
public class Position implements Comparable<Position> {
    public static final Pattern PATTERN = Pattern.compile("\\((?<line>[0-9]+), (?<column>[0-9]+)\\)");

    public static final Position ORIGIN = Position.fromZeroBased(0, 0);

    private static final Pattern LINE_SEPARATOR = Pattern.compile("(\n)|(\r)|(\r\n)");

    /*
    Implementation note: This class is designed to remove
    all ambiguity wrt zero-based and one-based line and
    column indexing. The motivating philosophy is that all
    indexing should be zero-based in any programming
    context, unless one is forced to use one-based indexing
    in order to interface with someone else's software. This
    justifies the apparent ambivalence here wrt zero vs.
    one: Zero should be used when possible, but one can be
    used when necessary.
    This philosophy (and the need to be Comparable)
    explains the choice not to use
    org.eclipse.xtext.util.LineAndColumn.
     */

    private final int line;
    private final int column;

    /* ------------------------  CONSTRUCTORS  -------------------------- */

    /**
     * Returns the Position that describes the given
     * zero-based line and column numbers.
     * @param line the zero-based line number
     * @param column the zero-based column number
     * @return a Position describing the position described
     * by <code>line</code> and <code>column</code>.
     */
    public static Position fromZeroBased(int line, int column) {
        return new Position(line, column);
    }

    /**
     * Returns the Position that describes the given
     * one-based line and column numbers.
     * @param line the one-based line number
     * @param column the one-based column number
     * @return a Position describing the position described
     * by <code>line</code> and <code>column</code>.
     */
    public static Position fromOneBased(int line, int column) {
        return new Position(line - 1, column - 1);
    }

    /**
     * Returns the Position that equals the displacement
     * caused by <code>text</code>.
     * @param text an arbitrary string
     * @return the Position that equals the displacement
     * caused by <code>text</code>
     */
    public static Position displacementOf(String text) {
        String[] lines = text.lines().toArray(String[]::new);
        if (lines.length == 0) return ORIGIN;
        return Position.fromZeroBased(lines.length - 1, lines[lines.length - 1].length());
    }

    /**
     * Returns the Position that describes the same location
     * in <code>content</code> as <code>offset</code>.
     * @param offset a location, expressed as an offset from
     *               the beginning of <code>content</code>
     * @param content the content of a document
     * @return the Position that describes the same location
     * in <code>content</code> as <code>offset</code>
     */
    public static Position fromOffset(int offset, String content) {
        int lineNumber = 0;
        Matcher matcher = LINE_SEPARATOR.matcher(content);
        int start = 0;
        while (matcher.find(start)) {
            if (matcher.start() > offset) return Position.fromZeroBased(lineNumber, offset - start);
            start = matcher.end();
            lineNumber++;
        }
        return Position.fromZeroBased(lineNumber, offset);
    }

    /**
     * Creates a new Position with the given line and column
     * numbers. Private so that unambiguously named factory
     * methods must be used instead.
     * @param line the zero-based line number
     * @param column the zero-based column number
     */
    private Position(int line, int column) {
        // Assertions about whether line and column are
        // non-negative are deliberately omitted. Positions
        // can be relative.
        this.line = line;
        this.column = column;
    }

    /* -----------------------  PUBLIC METHODS  ------------------------- */

    /**
     * Returns the one-based line number described by this
     * <code>Position</code>.
     * @return the one-based line number described by this
     * <code>Position</code>
     */
    public int getOneBasedLine() {
        return line + 1;
    }

    /**
     * Returns the one-based column number described by this
     * <code>Position</code>.
     * @return the one-based column number described by this
     * <code>Position</code>
     */
    public int getOneBasedColumn() {
        return column + 1;
    }

    /**
     * Returns the zero-based line number described by this
     * <code>Position</code>.
     * @return the zero-based line number described by this
     * <code>Position</code>
     */
    public int getZeroBasedLine() {
        return line;
    }

    /**
     * Returns the zero-based column number described by this
     * <code>Position</code>.
     * @return the zero-based column number described by this
     * <code>Position</code>
     */
    public int getZeroBasedColumn() {
        return column;
    }

    /**
     * Returns the offset of this <code>Position</code> from
     * the beginning of the document whose content is
     * <code>documentContent</code>. Silently returns an
     * incorrect but valid offset in the case that this
     * <code>Position</code> is not contained in
     * <code>documentContent</code>.
     * @param documentContent the content of the document
     *                        in which this is a position
     * @return the offset of this <code>Position</code> from
     * the beginning of the document whose content is
     * <code>documentContent</code>
     */
    public int getOffset(String documentContent) {
        return documentContent.lines().limit(getZeroBasedLine()).mapToInt(String::length).sum()
            + getZeroBasedColumn() + getZeroBasedLine(); // Final term accounts for line breaks
    }

    /**
     * Returns the sum of this and another <code>Position
     * </code>. The result has meaning because Positions are
     * relative.
     * @param other another <code>Position</code>
     * @return the sum of this and <code>other</code>
     */
    public Position plus(Position other) {
        return new Position(line + other.line, column + other.column);
    }

    /**
     * Returns the difference of this and another <code>
     * Position</code>. The result has meaning because
     * Positions are relative.
     * @param other another <code>Position</code>
     * @return the difference of this and <code>other</code>
     */
    public Position minus(Position other) {
        return new Position(line - other.line, column - other.column);
    }

    /**
     * Compares two positions according to their order of
     * appearance in a document (first according to line,
     * then according to column).
     */
    @Override
    public int compareTo(Position o) {
        if (line != o.line) {
            return line - o.line;
        }
        return column - o.column;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Position && ((Position) obj).compareTo(this) == 0;
    }

    @Override
    public String toString() {
        return String.format("(%d, %d)", getZeroBasedLine(), getZeroBasedColumn());
    }

    public static Position fromString(String s) {
        Matcher matcher = PATTERN.matcher(s);
        if (matcher.matches()) {
            return Position.fromZeroBased(
                Integer.parseInt(matcher.group("line")),
                Integer.parseInt(matcher.group("column"))
            );
        }
        throw new IllegalArgumentException(String.format("Could not parse %s as a Position.", s));
    }

    @Override
    public int hashCode() {
        return line * 31 + column;
    }

    /**
     * Removes the names from the named capturing groups
     * that appear in <code>regex</code>.
     * @param regex an arbitrary regular expression
     * @return a string representation of <code>regex</code>
     * with the names removed from the named capturing
     * groups
     */
    public static String removeNamedCapturingGroups(Pattern regex) {  // FIXME: Does this belong here?
        return regex.toString().replaceAll("\\(\\?<\\w+>", "(");
    }
}
