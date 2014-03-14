package org.revapi.basic;

import java.util.regex.Pattern;

import org.revapi.Element;
import org.revapi.Report;

/**
 * A helper class to {@link org.revapi.basic.AbstractDifferenceReferringTransform} that defines the match of
 * a configuration element and a difference.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public abstract class DifferenceMatchRecipe {
    boolean regex;
    String code;
    Pattern codeRegex;
    String oldElement;
    Pattern oldElementRegex;
    String newElement;
    Pattern newElementRegex;

    public boolean matches(Report.Difference difference, Element oldElement, Element newElement) {
        if (regex) {
            return codeRegex.matcher(difference.code).matches() &&
                (oldElementRegex == null ||
                    oldElementRegex.matcher(oldElement.getFullHumanReadableString()).matches()) &&
                (newElementRegex == null ||
                    newElementRegex.matcher(newElement.getFullHumanReadableString()).matches());
        } else {
            return code.equals(difference.code) &&
                (this.oldElement == null || this.oldElement.equals(oldElement.getFullHumanReadableString())) &&
                (this.newElement == null || this.newElement.equals(newElement.getFullHumanReadableString()));
        }
    }

    public abstract Report.Difference transformMatching(Report.Difference difference, Element oldElement,
        Element newElement);
}
