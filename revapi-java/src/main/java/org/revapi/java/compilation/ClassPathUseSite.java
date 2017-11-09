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
package org.revapi.java.compilation;

import javax.lang.model.element.Element;

import org.revapi.java.spi.UseSite;

/**
 * @author Lukas Krejci
 * @since 0.11.0
 */
public final class ClassPathUseSite {
    public final UseSite.Type useType;
    public final Element site;
    public final int indexInParent;

    ClassPathUseSite(UseSite.Type useType, Element site, int indexInParent) {
        this.useType = useType;
        this.site = site;
        this.indexInParent = indexInParent;
    }

    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ClassPathUseSite that = (ClassPathUseSite) o;

        if (useType != that.useType) {
            return false;
        }

        return site.equals(that.site);

    }

    @Override public int hashCode() {
        int result = useType.hashCode();
        result = 31 * result + site.hashCode();
        return result;
    }
}
