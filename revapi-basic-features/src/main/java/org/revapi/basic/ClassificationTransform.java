package org.revapi.basic;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.ChangeSeverity;
import org.revapi.CompatibilityType;
import org.revapi.Element;
import org.revapi.Report;

/**
 * A generic difference transform that can change the classification of a difference. This can be used in situations
 * where one wants to consider certain differences differently than the defining extension declared them.
 * <p/>
 * The transform can be configured like so:
 * <pre><code>
 * revapi.reclassify.1.regex=false
 * revapi.reclassify.1.code=PROBLEM_CODE;
 * revapi.reclassify.1.old=FULL_REPRESENTATION_OF_THE_OLD_ELEMENT
 * revapi.reclassify.1.new=FULL_REPRESENTATION_OF_THE_NEW_ELEMENT
 * revapi.reclassify.1.NEW_COMPATIBILITY_TYPE=NEW_SEVERITY
 * revapi.reclassify.1.NEW_COMPATIBILITY_TYPE=NEW_SEVERITY
 * revapi.reclassify.1.NEW_COMPATIBILITY_TYPE=NEW_SEVERITY
 * </code></pre>
 * <p/>
 * The {@code code} is mandatory (obviously). The {@code old} and {@code new} properties are optional and the rule will
 * match when all the specified properties of it match. If regex attribute is "true" (defaults to "false"), all the
 * code, old and new are understood as regexes.
 * <p/>
 * The {@code NEW_COMPATIBILITY_TYPE} corresponds to one of the names of the {@link org.revapi.CompatibilityType}
 * enum and the {@code NEW_SEVERITY} corresponds to one of the names of the {@link org.revapi.ChangeSeverity}
 * enum. The reclassified difference inherits its classification (i.e. the compatibility type + severity pairs) and
 * only
 * redefines the ones explicitly defined in the configuration.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public class ClassificationTransform
    extends AbstractDifferenceReferringTransform<ClassificationTransform.ClassificationRecipe, Void> {

    public static class ClassificationRecipe extends DifferenceMatchRecipe {
        protected final Map<CompatibilityType, ChangeSeverity> classification = new HashMap<>();

        @Override
        public Report.Difference transformMatching(Report.Difference difference, Element oldElement,
            Element newElement) {
            if (classification.isEmpty()) {
                return difference;
            } else {
                return Report.Difference.builder().withCode(difference.code).withName(difference.name)
                    .withDescription(difference.description).addAttachments(difference.attachments)
                    .addClassifications(difference.classification).addClassifications(classification).build();
            }
        }
    }

    public ClassificationTransform() {
        super("revapi.reclassify");
    }

    @Nullable
    @Override
    protected Void initConfiguration() {
        return null;
    }

    @Nonnull
    @Override
    protected ClassificationRecipe newRecipe(Void context) {
        return new ClassificationRecipe();
    }

    @Override
    protected void assignToRecipe(@Nullable Void context, @Nonnull ClassificationRecipe recipe,
        @Nonnull String key, @Nullable String value) {

        try {
            CompatibilityType comp = CompatibilityType.valueOf(key);
            ChangeSeverity sev = value == null ? null : ChangeSeverity.valueOf(value);
            recipe.classification.put(comp, sev);
        } catch (IllegalArgumentException ignored) {
            super.assignToRecipe(context, recipe, key, value);
        }
    }

    @Override
    public void close() {
    }
}
