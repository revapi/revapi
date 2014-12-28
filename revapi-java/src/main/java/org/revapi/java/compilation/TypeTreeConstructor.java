package org.revapi.java.compilation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.revapi.Archive;
import org.revapi.java.model.MissingClassElement;
import org.revapi.java.model.TypeElement;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.UseSite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is essentially a helper class to {@link org.revapi.java.compilation.ClassTreeInitializer}.
 * It keeps track of what classes have been seen and what classes have been referenced.
 *
 * @author Lukas Krejci
 * @since 0.2.0
 */
final class TypeTreeConstructor {
    private static final Logger LOG = LoggerFactory.getLogger(TypeTreeConstructor.class);

    private final Set<String> unseenClassesBinaryNames = new HashSet<>();
    private final Map<String, TypeRecord> typesByBinaryName = new HashMap<>();
    private final ProbingEnvironment environment;
    private final Set<File> bootstrapClasspath;
    private Set<String> bootstrapClasses;

    TypeTreeConstructor(ProbingEnvironment environment, Set<File> bootstrapClasspath) {
        this.environment = environment;
        this.bootstrapClasspath = bootstrapClasspath;
    }

    public ClassProcessor createApiClassProcessor(Archive classArchive, String classBinaryName, boolean apiType) {
        return new ClassProcessor(classArchive, classBinaryName, apiType);
    }

    public boolean hasUnknownClasses() {
        return !unseenClassesBinaryNames.isEmpty();
    }

    public Results construct() {
        Comparator<TypeRecord> nestingComparator = new Comparator<TypeRecord>() {
            @Override
            public int compare(TypeRecord o1, TypeRecord o2) {
                return o1.getNestingDepth() - o2.getNestingDepth();
            }
        };

        List<TypeRecord> allTypes = new ArrayList<>(typesByBinaryName.values());

        Collections.sort(allTypes, nestingComparator);

        List<String> unknownTypes = new ArrayList<>();

        for (TypeRecord t : allTypes) {
            if (t.isApiType()) {
                if (t.getType() == null) {
                    unknownTypes.add(t.getBinaryName());
                    continue;
                }

                //now check that the type is not an inner class or if it is
                //that it's parent has already been added into the environment.
                //we can do that because we sorted the types by their nesting
                //depth and so all the parents will have been added to the env
                //before their children.
                if (t.getOwner() == null) {
                    environment.getTree().getRootsUnsafe().add(t.getType());
                } else if (t.isApiThroughUse() && checkInTree(t.getOwner()) == null) {
                    environment.getTree().getRootsUnsafe().add(t.getType());
                }

                if (t.hasUseSites()) {
                    environment.getUseSiteMap().put(t.getBinaryName(), t.getUseSites());
                }
            }
        }

        return new Results(unknownTypes);
    }

    private TypeRecord checkInTree(TypeRecord t) {
        if (t == null) {
            return null;
        }

        Set<TypeElement> roots = environment.getTree().getRootsUnsafe();

        if (t.getOwner() == null) {
            //top-level classes
            return roots.contains(t.getType()) ? t : null;
        } else if (roots.contains(t.getType())) {
            //inner classes with owners outside of API
            return t;
        } else {
            //inner classes
            return checkInTree(t.getOwner());
        }
    }

    private TypeRecord getOrCreateTypeRecord(String typeBinaryName) {
        TypeRecord rec = typesByBinaryName.get(typeBinaryName);
        if (rec == null) {
            rec = new TypeRecord(typeBinaryName);
            typesByBinaryName.put(typeBinaryName, rec);
            unseenClassesBinaryNames.add(typeBinaryName);
        }

        return rec;
    }

    private TypeElement createTypeElement(Archive archive, String binaryName, String canonicalName, boolean asError) {
        if (asError) {
            return new MissingClassElement(environment, binaryName, canonicalName);
        } else {
            return new TypeElement(environment, archive, binaryName, canonicalName);
        }
    }

    private boolean isOnBootstrapClasspath(String typeBinaryName) {
        if (typeBinaryName.startsWith("java.")) {

            //quick check for many of the cases
            return true;
        }

        if (bootstrapClasses == null) {
            long time = 0;

            if (LOG.isTraceEnabled()) {
                LOG.trace("Building bootstrap classes cache");
                time = System.currentTimeMillis();
            }

            bootstrapClasses = new HashSet<>();
            for (File f : bootstrapClasspath) {
                ZipFile jar = null;
                try {
                    jar = new ZipFile(f);
                    Enumeration<? extends ZipEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (name.endsWith(".class")) {
                            name = name.substring(0, name.length() - 6).replace('/', '.');
                            bootstrapClasses.add(name);
                        }
                    }
                    jar.close();
                } catch (IOException e) {
                    LOG.error("Failed to analyze bootstrap class path entry at " + f.getAbsolutePath(), e);
                } finally {
                    try {
                        if (jar != null) {
                            jar.close();
                        }
                    } catch (IOException e) {
                        LOG.warn("Failed to close bootstrap classpath entry (" + f.getAbsolutePath() + ").", e);
                    }
                }
            }


            if (LOG.isTraceEnabled()) {
                LOG.trace("Bootstrap classpath cache built in " + (System.currentTimeMillis() - time) +
                    "ms containing " + bootstrapClasses.size() + " entries.");
            }
        }

        return bootstrapClasses.contains(typeBinaryName);
    }

    public final static class Results {
        private final List<String> unknownTypeBinaryNames;

        private Results(List<String> unknownTypeBinaryNames) {
            this.unknownTypeBinaryNames = unknownTypeBinaryNames;
        }

        public List<String> getUnknownTypeBinaryNames() {
            return unknownTypeBinaryNames;
        }
    }

    public final class ClassProcessor {
        private final Archive archive;
        private final String classBinaryName;
        private final boolean apiType;
        private InnerClassHierarchyConstructor innerClassHierarchyConstructor;

        private ClassProcessor(Archive archive, String classBinaryName, boolean apiType) {
            this.archive = archive;
            this.classBinaryName = classBinaryName;
            this.apiType = apiType;
        }

        public InnerClassHierarchyConstructor getInnerClassHierarchyConstructor() {
            if (innerClassHierarchyConstructor == null) {
                innerClassHierarchyConstructor = new InnerClassHierarchyConstructor();
            }

            return innerClassHierarchyConstructor;
        }

        public void addUse(String usedTypeBinaryName, RawUseSite useSite) {
            if (isOnBootstrapClasspath(usedTypeBinaryName)) {
                return;
            }

            TypeRecord rec = getOrCreateTypeRecord(usedTypeBinaryName);

            //the used type is going to be part of the API if it is used
            //in a public position - i.e. by a class that is itself part of the API
            rec.setApiType(rec.isApiType() || apiType);
            rec.setApiThroughUse(rec.isApiThroughUse() || apiType);

            if (rec.isApiType()) {
                if (rec.getType() == null) {
                    unseenClassesBinaryNames.add(usedTypeBinaryName);
                }
                addUsedTypesToApi(rec, new HashSet<TypeRecord>());
            }

            rec.getUseSites().add(useSite);

            TypeRecord userRec = getOrCreateTypeRecord(useSite.getSiteClass());
            Map<String, EnumSet<UseSite.Type>> usedTypes = userRec.getUsedTypes();
            EnumSet<UseSite.Type> useTypes = usedTypes.get(usedTypeBinaryName);
            if (useTypes == null) {
                useTypes = EnumSet.noneOf(UseSite.Type.class);
                usedTypes.put(usedTypeBinaryName, useTypes);
            }
            useTypes.add(useSite.getUseType());
        }

        public void commitClass() {
            TypeRecord rec = getOrCreateTypeRecord(classBinaryName);
            unseenClassesBinaryNames.remove(classBinaryName);

            //if this was determined as part of API, don't reset it back (potentially)
            rec.setApiType(rec.isApiType() || apiType);

            if (rec.isApiType()) {
                addUsedTypesToApi(rec, new HashSet<TypeRecord>());
            }

            if (rec.getType() != null) {
                return;
            }

            if (innerClassHierarchyConstructor == null) {
                TypeElement type = createTypeElement(archive, classBinaryName, classBinaryName, false);
                rec.setType(type);
                initChildren(rec, 1);
            } else {
                //notice that we don't change the apiType flag for inner classes
                //for the current class, we already did it above, but we don't
                //do anything for the parents - their apiType flag will be set
                //when their classes are processed.
                List<InnerClass> innerClassHierarchy = innerClassHierarchyConstructor.process();
                if (innerClassHierarchy.isEmpty()) {
                    //anonymous inner class most probably
                    return;
                }

                TypeRecord owner = null;

                int nestingDepth = 0;

                for (InnerClass ic : innerClassHierarchy) {
                    TypeRecord type = partiallyCommitInnerClass(ic, owner, nestingDepth++);

                    if (owner != null) {
                        addUse(owner.getBinaryName(),
                            new RawUseSite(UseSite.Type.CONTAINS, RawUseSite.SiteType.CLASS, type.getBinaryName(), null,
                                null));
                        initChildren(owner, nestingDepth);
                    }

                    owner = type;
                }
            }
        }

        private void initChildren(TypeRecord owner, int nestingDepth) {
            //eagerly initialize the children using the javax.model
            //means so that at no point in time there exist
            //2 TypeElements for a single type.
            if (owner.getType() == null) {
                throw new IllegalStateException("At this point in time, owner should have its type set.");
            }

            for (JavaElement c : owner.getType().getChildren()) {
                if (!(c instanceof TypeElement)) {
                    continue;
                }

                TypeElement t = (TypeElement) c;
                TypeRecord r = getOrCreateTypeRecord(t.getBinaryName());
                r.setOwner(owner);
                r.setType(t);
                r.setNestingDepth(nestingDepth);
            }
        }

        private TypeRecord partiallyCommitInnerClass(InnerClass ic, TypeRecord owner, int nestingDepth) {
            TypeRecord rec = getOrCreateTypeRecord(ic.getBinaryName());

            if (rec.getType() == null) {
                TypeElement type = createTypeElement(archive, ic.getBinaryName(), ic.getCanonicalName(), false);

                rec.setType(type);
            }

            rec.setOwner(owner);
            rec.setNestingDepth(nestingDepth);

            return rec;
        }

        private void addUsedTypesToApi(TypeRecord userType, Set<TypeRecord> visitedTypes) {
            if (!userType.hasUsedTypes()) {
                return;
            }

            for (Map.Entry<String, EnumSet<UseSite.Type>> usedType : userType.getUsedTypes().entrySet()) {
                if (!movesToApi(usedType.getValue())) {
                    continue;
                }

                TypeRecord rec = getOrCreateTypeRecord(usedType.getKey());
                rec.setApiType(true);
                rec.setApiThroughUse(true);

                if (!visitedTypes.contains(rec)) {
                    visitedTypes.add(rec);
                    addUsedTypesToApi(rec, visitedTypes);
                }
            }
        }
    }

    private static boolean movesToApi(Collection<UseSite.Type> useTypes) {
        for (UseSite.Type useType : useTypes) {
            if (movesToApi(useType)) {
                return true;
            }
        }

        return false;
    }

    private static boolean movesToApi(UseSite.Type useType) {
        switch (useType) {
        case ANNOTATES: case CONTAINS:
            return false;
        default:
            return true;
        }
    }
}
