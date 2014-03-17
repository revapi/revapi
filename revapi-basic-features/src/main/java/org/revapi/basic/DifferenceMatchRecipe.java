package org.revapi.basic;

import java.util.regex.Pattern;

import org.jboss.dmr.ModelNode;
import org.revapi.Difference;
import org.revapi.Element;

/**
 * A helper class to {@link org.revapi.basic.AbstractDifferenceReferringTransform} that defines the match of
 * a configuration element and a difference.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public abstract class DifferenceMatchRecipe {
    final ModelNode config;
    final boolean regex;
    final String code;
    final Pattern codeRegex;
    final String oldElement;
    final Pattern oldElementRegex;
    final String newElement;
    final Pattern newElementRegex;

    protected DifferenceMatchRecipe(ModelNode config) {
        if (!config.has("code")) {
            throw new IllegalArgumentException("Difference code has to be specified.");
        }

        regex = config.has("regex") && config.get("regex").asBoolean();
        code = config.get("code").asString();
        codeRegex = regex ? Pattern.compile(code) : null;
        oldElement = config.has("old") ? config.get("old").asString() : null;
        oldElementRegex = regex && oldElement != null ? Pattern.compile(oldElement) : null;
        newElement = config.has("new") ? config.get("old").asString() : null;
        newElementRegex = regex && newElement != null ? Pattern.compile(newElement) : null;
        this.config = config;
    }

    public boolean matches(Difference difference, Element oldElement, Element newElement) {
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

    public abstract Difference transformMatching(Difference difference, Element oldElement,
        Element newElement);
}
