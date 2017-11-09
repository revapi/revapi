package org.revapi.simple;

import org.revapi.Element;
import org.revapi.ElementGateway;
import org.revapi.FilterResult;

public class SimpleElementGateway extends SimpleConfigurable implements ElementGateway {
    @Override
    public void start(AnalysisStage stage) {

    }

    @Override
    public FilterResult filter(AnalysisStage stage, Element element) {
        return FilterResult.doesntMatch();
    }

    @Override
    public void end(AnalysisStage stage) {

    }
}
