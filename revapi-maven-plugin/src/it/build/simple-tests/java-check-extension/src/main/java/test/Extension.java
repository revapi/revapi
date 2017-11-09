/*
 * Copyright 2014-2017 Lukas Krejci
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
package test;

import java.lang.Override;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.revapi.DifferenceSeverity;
import org.revapi.CompatibilityType;
import org.revapi.Difference;
import org.revapi.java.spi.JavaTypeElement;

public class Extension extends org.revapi.java.spi.CheckBase {

    @Override
    public void doVisitClass(JavaTypeElement oldType, JavaTypeElement newType) {
        if (oldType != null && !isMissing(oldType.getDeclaringElement()) && "test.Dep".equals(oldType.getDeclaringElement().getQualifiedName().toString())) {
            pushActive(oldType, newType);
        }
    }

    @Override
    public List<Difference> doEnd() {
        ActiveElements<JavaTypeElement> types = popIfActive();
        if (types != null) {
            return Collections.singletonList(
                Difference.builder().withCode("!!TEST_CODE!!").withName("test check")
                    .withDescription("test description")
                    .addClassification(CompatibilityType.SOURCE, DifferenceSeverity.BREAKING).build());
        }

        return null;
    }

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.CLASS);
    }
}
