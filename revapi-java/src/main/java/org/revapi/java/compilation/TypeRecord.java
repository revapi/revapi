package org.revapi.java.compilation;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.java.model.TypeElement;
import org.revapi.java.spi.UseSite;

/**
 * A helper to {@link org.revapi.java.compilation.TypeTreeConstructor}.
 * This class contains attributes of types recorded during the type tree
 * construction, using which the type tree constructor will determine
 * the public API of a set of archives.
 *
 * @author Lukas Krejci
 * @since 0.2.0
 */
final class TypeRecord {
    private final String binaryName;

    private boolean apiType;
    private boolean apiThroughUse;
    private Set<RawUseSite> useSites;
    private Map<String, EnumSet<UseSite.Type>> usedTypes;
    private TypeElement type;
    private TypeRecord owner;
    private int nestingDepth;

    TypeRecord(String binaryName) {
        this.binaryName = binaryName;
    }

    /**
     * The binary name of the type
     */
    public String getBinaryName() {
        return binaryName;
    }

    /**
     * Whether or not this type is part of API.
     * Note that this information can change during the tree construction.
     * Once it's set to true, it should never set back to false though.
     */
    public boolean isApiType() {
        return apiType;
    }

    public void setApiType(boolean apiType) {
        this.apiType = apiType;
    }

    /**
     * Distinguishing a true API type and an API type that was pulled
     * into the API through its use in a public capacity on some other
     * API class.
     */
    public boolean isApiThroughUse() {
        return apiThroughUse;
    }

    public void setApiThroughUse(boolean apiThroughUse) {
        this.apiThroughUse = apiThroughUse;
    }

    /**
     * Returns the sites that use this type.
     */
    @Nonnull
    public Set<RawUseSite> getUseSites() {
        if (useSites == null) {
            useSites = new HashSet<>();
        }
        return useSites;
    }

    public boolean hasUseSites() {
        return useSites != null && !useSites.isEmpty();
    }
    /**
     * Returns the types that this type is using in a accessible capacity along with the ways
     * it is using them.
     * (i.e. as a return type of a public method, type of a public field, etc.).
     */
    @Nonnull
    public Map<String, EnumSet<UseSite.Type>> getUsedTypes() {
        if (usedTypes == null) {
            usedTypes = new HashMap<>();
        }
        return usedTypes;
    }

    public boolean hasUsedTypes() {
        return usedTypes != null && !usedTypes.isEmpty();
    }

    /**
     * The generated TypeElement for this type.
     * This will be null until the class is actually committed into the tree
     * in the tree constructor.
     */
    @Nullable
    public TypeElement getType() {
        return type;
    }

    public void setType(TypeElement type) {
        this.type = type;
    }

    /**
     * Owner of an inner class or null for top-level classes.
     */
    public TypeRecord getOwner() {
        return owner;
    }

    public void setOwner(TypeRecord owner) {
        this.owner = owner;
    }

    /**
     * The number of class owners.
     * This is used for consistently sorting the set of all classes
     * when finalizing the "shape" of the type tree.
     */
    public int getNestingDepth() {
        return nestingDepth;
    }

    public void setNestingDepth(int nestingDepth) {
        this.nestingDepth = nestingDepth;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TypeRecord[");
        sb.append("binaryName='").append(binaryName).append('\'');
        sb.append(", apiType=").append(apiType);
        sb.append(']');
        return sb.toString();
    }
}
