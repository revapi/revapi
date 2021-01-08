/*
 * Copyright 2014-2021 Lukas Krejci
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.revapi.jackson;

import static java.util.Collections.singletonList;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.revapi.CompatibilityType;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.Report;
import org.revapi.base.BaseDifferenceAnalyzer;

public abstract class JacksonDifferenceAnalyzer<E extends JacksonElement<E>> extends BaseDifferenceAnalyzer<E> {
    // these really should be deques but those do not support nulls and we need to store nulls
    private final List<E> currentOldPath = new ArrayList<>();
    private final List<E> currentNewPath = new ArrayList<>();

    @Override
    public void beginAnalysis(@Nullable E oldElement, @Nullable E newElement) {
        currentOldPath.add(oldElement);
        currentNewPath.add(newElement);
    }

    @Override
    public Report endAnalysis(@Nullable E oldElement, @Nullable E newElement) {
        E oldEl = currentOldPath.get(currentOldPath.size() - 1);
        E newEl = currentNewPath.get(currentNewPath.size() - 1);

        Report.Builder bld = Report.builder().withOld(oldEl).withNew(newEl);

        Difference.InReportBuilder dbld = null;
        String path = null;
        String file = null;
        if (oldEl == null) {
            dbld = bld.addProblem();
            addAdded(dbld);
            path = path(currentNewPath);
            file = newEl.filePath;
        } else if (newEl == null) {
            dbld = bld.addProblem();
            addRemoved(dbld);
            path = path(currentOldPath);
            file = oldEl.filePath;
        } else if (!oldEl.equals(newEl)) {
            dbld = bld.addProblem();
            addChanged(dbld);
            path = path(currentNewPath);
            file = newEl.filePath;
            dbld.addAttachment("oldValue", oldEl.getValueString());
            dbld.addAttachment("newValue", newEl.getValueString());
        }

        if (dbld != null) {
            dbld.addAttachment("file", file);
            dbld.addAttachment("path", path);
            dbld.withIdentifyingAttachments(singletonList("path"));
            dbld.done();
        }

        currentOldPath.remove(currentOldPath.size() - 1);
        currentNewPath.remove(currentNewPath.size() - 1);

        return bld.build();
    }

    protected void addRemoved(Difference.InReportBuilder bld) {
        bld.withCode(valueRemovedCode())
                .withName("value removed")
                .addClassification(CompatibilityType.SEMANTIC, DifferenceSeverity.BREAKING);
    }

    protected void addAdded(Difference.InReportBuilder bld) {
        bld.withCode(valueAddedCode())
                .withName("value added")
                .addClassification(CompatibilityType.SEMANTIC, DifferenceSeverity.POTENTIALLY_BREAKING);
    }

    protected void addChanged(Difference.InReportBuilder bld) {
        bld.withCode(valueChangedCode())
                .withName("value changed")
                .addClassification(CompatibilityType.SEMANTIC, DifferenceSeverity.POTENTIALLY_BREAKING);
    }

    protected abstract String valueRemovedCode();

    protected abstract String valueAddedCode();

    protected abstract String valueChangedCode();

    protected String path(List<E> path) {
        StringBuilder sb = new StringBuilder();
        for (int i = path.size() - 1; i >= 0; --i) {
            E e = path.get(i);
            sb.append("/");
            if (e.keyInParent != null) {
                sb.append(e.keyInParent);
            } else if (e.indexInParent >= 0) {
                sb.append(e.indexInParent);
            }
        }

        return sb.toString();
    }
}
