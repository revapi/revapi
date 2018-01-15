package org.revapi.java.compilation;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

public class UseSitePath {
    public final TypeElement owner;
    public final Element useSite;

    UseSitePath(TypeElement owner, Element useSite) {
        this.owner = owner;
        this.useSite = useSite;
    }
}
