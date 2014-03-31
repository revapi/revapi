package org.revapi.java;

import javax.annotation.Nonnull;

import org.revapi.CompatibilityType;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class JavaCompatibility extends CompatibilityType {

    public static final CompatibilityType SOURCE = new JavaCompatibility("java.source");
    public static final CompatibilityType BINARY = new JavaCompatibility("java.binary");
    public static final CompatibilityType SEMANTIC = new JavaCompatibility("java.semantic");
    public static final CompatibilityType REFLECTION = new JavaCompatibility("java.reflection");

    public JavaCompatibility(@Nonnull String name) {
        super(name);
    }
}
