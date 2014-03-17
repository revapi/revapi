package org.revapi.basic;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jboss.dmr.ModelNode;
import org.revapi.ChangeSeverity;
import org.revapi.CompatibilityType;
import org.revapi.Difference;
import org.revapi.Element;

/**
 * A generic difference transform that can change the classification of a difference. This can be used in situations
 * where one wants to consider certain differences differently than the defining extension declared them.
 * <p/>
 * The transform can be configured like so:
 * <pre><code>
 * {
 *  "revapi" : {
 *      "reclassify" : [
 *          {
 *              "regex" : false,
 *              "code" : "PROBLEM_CODE",
 *              "old" : "FULL_REPRESENTATION_OF_THE_OLD_ELEMENT",
 *              "new" : "FULL_REPRESENTATION_OF_THE_NEW_ELEMENT",
 *              classify : {
 *                  "NEW_COMPATIBILITY_TYPE": "NEW_SEVERITY",
 *                  "NEW_COMPATIBILITY_TYPE_2": "NEW_SEVERITY_2",
 *              }
 *          },
 *          ...
 *      ]
 *  }
 * }
 * </code></pre>
 * <p/>
 * The {@code code} is mandatory (obviously). The {@code old} and {@code new} properties are optional and the rule will
 * match when all the specified properties of it match. If regex attribute is "true" (defaults to "false"), all the
 * code, old and new are understood as regexes (java regexes, not javascript ones).
 * <p/>
 * The {@code NEW_COMPATIBILITY_TYPE} corresponds to one of the names of the {@link org.revapi.CompatibilityType}
 * enum and the {@code NEW_SEVERITY} corresponds to one of the names of the {@link org.revapi.ChangeSeverity}
 * enum. The reclassified difference inherits its classification (i.e. the compatibility type + severity pairs) and
 * only redefines the ones explicitly defined in the configuration.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public class ClassificationTransform
    extends AbstractDifferenceReferringTransform<ClassificationTransform.ClassificationRecipe, Void> {

    public static class ClassificationRecipe extends DifferenceMatchRecipe {
        protected final Map<CompatibilityType, ChangeSeverity> classification = new EnumMap<>(CompatibilityType.class);

        public ClassificationRecipe(ModelNode node) {
            super(node);
            ModelNode classfications = node.get("classify");
            for (CompatibilityType ct : CompatibilityType.values()) {
                if (classfications.has(ct.name())) {
                    String val = classfications.get(ct.name()).asString();
                    ChangeSeverity sev = ChangeSeverity.valueOf(val);
                    classification.put(ct, sev);
                }
            }
        }

        @Override
        public Difference transformMatching(Difference difference, Element oldElement,
            Element newElement) {
            if (classification.isEmpty()) {
                return difference;
            } else {
                return Difference.builder().withCode(difference.code).withName(difference.name)
                    .withDescription(difference.description).addAttachments(difference.attachments)
                    .addClassifications(difference.classification).addClassifications(classification).build();
            }
        }
    }

    public ClassificationTransform() {
        super("revapi", "reclassify");
    }

    @Nullable
    @Override
    protected Void initConfiguration() {
        return null;
    }

    @Nonnull
    @Override
    protected ClassificationRecipe newRecipe(Void context, ModelNode config) {
        return new ClassificationRecipe(config);
    }

    @Override
    public void close() {
    }
}
