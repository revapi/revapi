package test;

import java.lang.Override;
import java.util.Collections;
import java.util.List;

import javax.lang.model.element.TypeElement;

import org.revapi.ChangeSeverity;
import org.revapi.CompatibilityType;
import org.revapi.MatchReport;

public class Extension extends org.revapi.java.CheckBase {

    @Override
    public void doVisitClass(TypeElement oldType, TypeElement newType) {
        if ("test.Dep".equals(oldType.getQualifiedName().toString())) {
            pushActive(oldType, newType);
        }
    }

    @Override
    public List<MatchReport.Problem> doEnd() {
        ActiveElements<TypeElement> types = popIfActive();
        if (types != null) {
            return Collections.singletonList(
                MatchReport.Problem.builder().withCode("!!TEST_CODE!!").withName("test check")
                    .withDescription("test description")
                    .addClassification(CompatibilityType.SOURCE, ChangeSeverity.BREAKING).build());
        }

        return null;
    }
}
