package org.revapi.java.transforms.annotations;

import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jboss.dmr.ModelNode;
import org.revapi.AnalysisContext;
import org.revapi.CompatibilityType;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.DifferenceTransform;
import org.revapi.java.model.AnnotationElement;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 * @since 0.12.0
 */
public final class DownplayHarmlessAnnotationChanges implements DifferenceTransform<JavaModelElement> {
    private boolean skip = false;

    private static final Set<String> HARMLESS_ANNOTATIONS = new HashSet<>(Arrays.asList(
            "@java.lang.FunctionalInterface", //having this is purely informational
            "@java.lang.annotation.Documented" //this doesn't affect the runtime at any rate
    ));

    @Nonnull @Override public Pattern[] getDifferenceCodePatterns() {
        return new Pattern[] {exact("java.annotation.added"), exact("java.annotation.removed"),
                exact("java.annotation.attributeValueChanged"), exact("java.annotation.attributeAdded"),
                exact("java.annotation.attributeRemoved")};
    }

    @Nullable @Override
    public Difference transform(@Nullable JavaModelElement oldElement, @Nullable JavaModelElement newElement,
                                @Nonnull Difference difference) {
        if (skip) {
            return difference;
        }

        if (!(difference.attachments.get(0) instanceof AnnotationElement)) {
            return difference;
        }

        AnnotationElement anno = (AnnotationElement) difference.attachments.get(0);

        if (HARMLESS_ANNOTATIONS.contains(anno.getFullHumanReadableString())) {
            return new Difference(difference.code, difference.name, difference.description,
                    reclassify(difference.classification), difference.attachments);
        } else {
            return difference;
        }
    }

    @Override public void close() throws Exception {
    }

    @Nullable @Override public String[] getConfigurationRootPaths() {
        return new String[] {"revapi.java.downplayHarmlessAnnotationChanges"};
    }

    @Nullable @Override public Reader getJSONSchema(@Nonnull String configurationRootPath) {
        return new StringReader("{\"type\": \"boolean\"}");
    }

    @Override public void initialize(@Nonnull AnalysisContext analysisContext) {
        ModelNode conf = analysisContext.getConfiguration()
                .get("revapi", "java", "downplayHarmlessAnnotationChanges");

        if (conf.isDefined()) {
            skip = !conf.asBoolean();
        }
    }

    private static Pattern exact(String string) {
        return Pattern.compile("^" + Pattern.quote(string) + "$");
    }

    private static Map<CompatibilityType, DifferenceSeverity> reclassify(Map<CompatibilityType, DifferenceSeverity> orig) {
        return orig.keySet().stream()
                .collect(Collectors.toMap(Function.identity(), v -> DifferenceSeverity.EQUIVALENT));
    }
}
