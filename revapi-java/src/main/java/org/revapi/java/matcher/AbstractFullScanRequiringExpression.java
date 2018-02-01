package org.revapi.java.matcher;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.revapi.ElementGateway;
import org.revapi.FilterMatch;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaModelElement;

abstract class AbstractFullScanRequiringExpression implements MatchExpression {
    private final MatchExpression scan;
    private final List<JavaElement> matchedInScan;
    private final List<JavaElement> undecidedInScan;

    AbstractFullScanRequiringExpression(MatchExpression scan) {
        this.scan = scan;
        matchedInScan = new ArrayList<>();
        undecidedInScan = new ArrayList<>();
    }

    @Override
    public final FilterMatch matches(ElementGateway.AnalysisStage stage, JavaModelElement element) {
        return prescanOrMatch(stage, element, this::matchesAfterScan);
    }

    protected abstract FilterMatch matchesAfterScan(JavaModelElement element);

    @Override
    public final FilterMatch matches(ElementGateway.AnalysisStage stage, JavaAnnotationElement annotation) {
        return prescanOrMatch(stage, annotation, this::matchesAfterScan);
    }

    protected abstract FilterMatch matchesAfterScan(JavaAnnotationElement element);

    @Override
    public final FilterMatch matches(ElementGateway.AnalysisStage stage, AnnotationAttributeElement attribute) {
        return prescanOrMatch(stage, attribute, this::matchesAfterScan);
    }

    protected abstract FilterMatch matchesAfterScan(AnnotationAttributeElement element);

    @Override
    public final FilterMatch matches(ElementGateway.AnalysisStage stage, TypeParameterElement typeParameter) {
        return prescanOrMatch(stage, typeParameter, this::matchesAfterScan);
    }

    protected abstract FilterMatch matchesAfterScan(TypeParameterElement element);

    protected List<JavaElement> getMatchedInScan() {
        return matchedInScan;
    }

    private <E extends JavaElement> FilterMatch prescanOrMatch(ElementGateway.AnalysisStage stage, E element, Function<E, FilterMatch> match) {
        if (stage == ElementGateway.AnalysisStage.FOREST_INCOMPLETE) {
            prescan(element);
            return FilterMatch.UNDECIDED;
        } else {
            completeUndecidedScans();
            return match.apply(element);
        }
    }

    private void prescan(JavaElement element) {
        FilterMatch match = scan.matches(ElementGateway.AnalysisStage.FOREST_INCOMPLETE, element);
        switch (match) {
            case MATCHES:
                matchedInScan.add(element);
                break;
            case UNDECIDED:
                undecidedInScan.add(element);
                break;
        }
    }

    private void completeUndecidedScans() {
        if (!undecidedInScan.isEmpty()) {
            for (JavaElement el : undecidedInScan) {
                FilterMatch match = scan.matches(ElementGateway.AnalysisStage.FOREST_COMPLETE, el);
                switch (match) {
                    case MATCHES:
                        matchedInScan.add(el);
                        break;
                    case UNDECIDED:
                        throw new IllegalStateException("Sub-expression [" + scan
                                + "] could not decidedly match element " + el + " even with a complete element forest" +
                                " available. This is a bug.");
                }
            }
            undecidedInScan.clear();
        }
    }
}
