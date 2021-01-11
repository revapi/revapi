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
package org.revapi.java;

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nonnull;
import javax.lang.model.util.Types;
import javax.tools.ToolProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.ApiAnalyzer;
import org.revapi.Archive;
import org.revapi.ArchiveAnalyzer;
import org.revapi.CoIterator;
import org.revapi.CorrespondenceComparatorDeducer;
import org.revapi.DifferenceAnalyzer;
import org.revapi.configuration.Configurable;
import org.revapi.configuration.JSONUtil;
import org.revapi.java.compilation.CompilationValve;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.model.JavaElementFactory;
import org.revapi.java.model.MethodElement;
import org.revapi.java.model.MethodParameterElement;
import org.revapi.java.model.TypeElement;
import org.revapi.java.spi.Check;
import org.revapi.java.spi.JarExtractor;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class JavaApiAnalyzer implements ApiAnalyzer<JavaElement> {
    private static final Logger LOG = LoggerFactory.getLogger(JavaApiAnalyzer.class);

    //see #forceClearCompilerCache for what these are
    private static final Method CLEAR_COMPILER_CACHE;
    private static final Object SHARED_ZIP_FILE_INDEX_CACHE;

    static {
        String javaVersion = System.getProperty("java.version");
        if (javaVersion.startsWith("1.")) {
            Method clearCompilerCache = null;
            Object sharedInstance = null;
            try {
                Class<?> zipFileIndexCacheClass = ToolProvider.getSystemToolClassLoader()
                        .loadClass("com.sun.tools.javac.file.ZipFileIndexCache");

                clearCompilerCache = zipFileIndexCacheClass.getDeclaredMethod("clearCache");
                Method getSharedInstance = zipFileIndexCacheClass.getDeclaredMethod("getSharedInstance");
                sharedInstance = getSharedInstance.invoke(null);
            } catch (Exception e) {
                LOG.warn("Failed to initialize the force-clearing of javac file caches. We will probably leak resources.", e);
            }

            if (clearCompilerCache != null && sharedInstance != null) {
                CLEAR_COMPILER_CACHE = clearCompilerCache;
                SHARED_ZIP_FILE_INDEX_CACHE = sharedInstance;
            } else {
                CLEAR_COMPILER_CACHE = null;
                SHARED_ZIP_FILE_INDEX_CACHE = null;
            }
        } else {
            CLEAR_COMPILER_CACHE = null;
            SHARED_ZIP_FILE_INDEX_CACHE = null;
        }
    }

    private final List<ExecutorService> compilationExecutors = new ArrayList<>(2);

    private AnalysisContext analysisContext;
    private AnalysisConfiguration configuration;
    private final Iterable<Check> checks;
    private final Iterable<JarExtractor> jarExtractors;
    private final List<CompilationValve> activeCompilations = new ArrayList<>(2);

    public JavaApiAnalyzer() {
        this(serviceLoad(Check.class), serviceLoad(JarExtractor.class));
    }

    private static <T> Iterable<T> serviceLoad(Class<T> type) {
        return ServiceLoader.load(type, JavaApiAnalyzer.class.getClassLoader());
    }

    public JavaApiAnalyzer(Iterable<Check> checks,
            Iterable<JarExtractor> archiveTransformers) {
        this.checks = checks;
        this.jarExtractors = archiveTransformers;
    }

    @Override
    public CorrespondenceComparatorDeducer<JavaElement> getCorrespondenceDeducer() {
        if (!configuration.isMatchOverloads()) {
            return CorrespondenceComparatorDeducer.naturalOrder();
        }
        
        return (l1, l2) -> {
            //so, we have to come up with some correspondence order... This is pretty easy for all java elements
            //but methods because of overloads and method parameters because they are positional.

            if (l1.isEmpty() || l2.isEmpty()) {
                return Comparator.naturalOrder();
            }

            // quickly peek inside to see if there even can be methods or method params in the lists - all of
            // the elements in either list will have a common parent and parents of both lists will have the same type
            // or be both null.
            JavaElement parent = l1.get(0).getParent();
            if (parent instanceof MethodElement) {
                // the method can contain method parameters but also annotations, so we have to be careful...

                // we will use this diff comparator to cleverly figure out how to transform the old params to the new
                // params with the min number of edits.
                Comparator<? super JavaElement> diff = CorrespondenceComparatorDeducer.
                        <JavaElement>diff((p1, p2) -> {
                            if (p1 instanceof MethodParameterElement && p2 instanceof MethodParameterElement) {
                                return ((MethodParameterElement) p1).getIndex()
                                        == ((MethodParameterElement) p2).getIndex();
                            } else {
                                return Objects.equals(p1, p2);
                            }
                        })
                        .sortAndGetCorrespondenceComparator(l1, l2);

                return (e1, e2) -> {
                    int ret = JavaElementFactory.compareByType(e1, e2);

                    if (ret != 0) {
                        return ret;
                    }

                    if (e1 instanceof MethodParameterElement && e2 instanceof MethodParameterElement) {
                        return diff.compare(e1, e2);
                    } else {
                        return e1.compareTo(e2);
                    }
                };
            } else if (!(parent instanceof TypeElement)) {
                return Comparator.naturalOrder();
            }

            IdentityHashMap<MethodElement, Integer> c1MethodOrder = new IdentityHashMap<>(l1.size());
            IdentityHashMap<MethodElement, Integer> c2MethodOrder = new IdentityHashMap<>(l2.size());

            //this will reorder the methods in the lists and will also fill in the method order indices in the maps
            //so that they can be used for comparisons below
            determineOrder(l1, l2, c1MethodOrder, c2MethodOrder);

            //and return a comparator
            return (e1, e2) -> {
                int ret = JavaElementFactory.compareByType(e1, e2);

                if (ret != 0) {
                    return ret;
                }

                //the only "special" treatment is required for methods - we determined the method order already, so
                //let's just look that up.
                if (e1 instanceof MethodElement && e2 instanceof MethodElement) {
                    MethodElement m1 = (MethodElement) e1;
                    MethodElement m2 = (MethodElement) e2;

                    return c1MethodOrder.get(m1) - c2MethodOrder.get(m2);
                } else {
                    return e1.compareTo(e2);
                }
            };
        };
    }

    private static void determineOrder(List<JavaElement> l1, List<JavaElement> l2,
            IdentityHashMap<MethodElement, Integer> l1MethodOrder,
            IdentityHashMap<MethodElement, Integer> l2MethodOrder) {

        TreeMap<String, List<MethodElement>> l1MethodsByName = new TreeMap<>();
        TreeMap<String, List<MethodElement>> l2MethodsByName = new TreeMap<>();

        int l1MethodsSize = addAllMethods(l1, l1MethodsByName);
        int l2MethodsSize = addAllMethods(l2, l2MethodsByName);

        //rehash overloads that are present in both collections - those are then reordered using their mutual
        //resemblance

        int index = 0;
        Iterator<Map.Entry<String, List<MethodElement>>> l1MethodsIterator = l1MethodsByName.entrySet().iterator();
        Iterator<Map.Entry<String, List<MethodElement>>> l2MethodsIterator = l2MethodsByName.entrySet().iterator();

        //iterate over the maps, sorted by name and assign the comparison index to the methods.
        //we iterate over the maps sorted by method name
        CoIterator<Map.Entry<String, List<MethodElement>>> coit = new CoIterator<>(l1MethodsIterator, l2MethodsIterator,
                Map.Entry.comparingByKey());

        List<JavaElement> l2MethodsInOrder = new ArrayList<>(l1MethodsSize);
        List<JavaElement> l1MethodsInOrder = new ArrayList<>(l2MethodsSize);

        while (coit.hasNext()) {
            coit.next();
            Map.Entry<String, List<MethodElement>> l1e = coit.getLeft();
            Map.Entry<String, List<MethodElement>> l2e = coit.getRight();

            if (l1e == null) {
                //no overloads with the name present in l1
                for (MethodElement m : l2e.getValue()) {
                    l2MethodOrder.put(m, index++);
                    l2MethodsInOrder.add(m);
                }
            } else if (l2e == null) {
                //no overloads with the name present in l2
                for (MethodElement m : l1e.getValue()) {
                    l1MethodOrder.put(m, index++);
                    l1MethodsInOrder.add(m);
                }
            } else {
                //overloads of the same name present in both maps
                //the lists were already sorted by the method above
                List<MethodElement> l1Overloads = l1e.getValue();
                List<MethodElement> l2Overloads = l2e.getValue();

                if (l1Overloads.size() == 1 && l2Overloads.size() == 1) {
                    //fast path for hopefully the vast majority of cases
                    //just indicate the same order for both methods from l1 and l2
                    MethodElement m1 = l1Overloads.get(0);
                    MethodElement m2 = l2Overloads.get(0);

                    l1MethodsInOrder.add(m1);
                    l2MethodsInOrder.add(m2);
                    l2MethodOrder.put(m2, index);
                    l1MethodOrder.put(m1, index++);
                } else {
                    //slow path - for each overload in l1, we need to pick the appropriate one from l2 and put it in the
                    //same place
                    List<MethodElement> as = l1Overloads;
                    List<MethodElement> bs = l2Overloads;
                    List<JavaElement> aio = l1MethodsInOrder;
                    List<JavaElement> bio = l2MethodsInOrder;
                    IdentityHashMap<MethodElement, Integer> ao = l1MethodOrder;
                    IdentityHashMap<MethodElement, Integer> bo = l2MethodOrder;

                    if (l1Overloads.size() > l2Overloads.size()) {
                        as = l2Overloads;
                        bs = l1Overloads;
                        aio = l2MethodsInOrder;
                        bio = l1MethodsInOrder;
                        ao = l2MethodOrder;
                        bo = l1MethodOrder;
                    }

                    // we will sort the method pairs by the levenshtein distance of their signatures
                    TreeMap<Integer, Map<MethodElement, List<MethodElement>>> methodsByDistance = new TreeMap<>();

                    int maxOverrides = bs.size();

                    for (MethodElement ma : as) {
                        String aRet = Util.toUniqueString(ma.getModelRepresentation().getReturnType());
                        String aErasedRet = Util.toUniqueString(ma.getTypeEnvironment().getTypeUtils()
                                .erasure(ma.getModelRepresentation().getReturnType()));

                        List<String> aParams = methodParamsSignature(ma, false);
                        List<String> aErasedParams = methodParamsSignature(ma, true);

                        for (MethodElement mb : bs) {
                            int distance = levenshteinDistance(aRet, aErasedRet, aParams, aErasedParams, mb);

                            methodsByDistance
                                    // we need to preserve the order so that the output is stable
                                    .computeIfAbsent(distance, __ -> new LinkedHashMap<>())
                                    .computeIfAbsent(ma, __ -> new ArrayList<>(maxOverrides))
                                    .add(mb);
                        }
                    }

                    // these are really used as identity hash sets, which the JDK lacks
                    IdentityHashMap<MethodElement, Void> unmatchedAs = new IdentityHashMap<>();
                    as.forEach(a -> unmatchedAs.put(a, null));
                    IdentityHashMap<MethodElement, Void> unmatchedBs = new IdentityHashMap<>();
                    bs.forEach(b -> unmatchedBs.put(b, null));

                    // now, going in the direction of increasing distance between methods, look up the first matching
                    // method pair that hasn't been processed yet.
                    for (Map<MethodElement, List<MethodElement>> matchingMethods : methodsByDistance.values()) {
                        for (Map.Entry<MethodElement, List<MethodElement>> e : matchingMethods.entrySet()) {
                            MethodElement ma = e.getKey();
                            List<MethodElement> mbs = e.getValue();

                            if (!unmatchedAs.containsKey(ma)) {
                                // we've already matched this method with something, so let's continue
                                continue;
                            }

                            for (MethodElement mb : mbs) {
                                if (!unmatchedBs.containsKey(mb)) {
                                    // this method has been matched already with some other method - this means we've
                                    // had a more precise match before.
                                    continue;
                                }

                                unmatchedAs.remove(ma);
                                unmatchedBs.remove(mb);

                                ao.put(ma, index);
                                aio.add(ma);
                                bo.put(mb, index++);
                                bio.add(mb);

                                // we're only matching method pairs and we've just matched one. we need to find a less
                                // precise match for the rest of the methods in mbs
                                break;
                            }
                        }
                    }

                    //add the rest
                    for (MethodElement m : unmatchedBs.keySet()) {
                        bo.put(m, index++);
                        bio.add(m);
                    }
                }
            }
        }

        //ok, so now we have the method indices right in the comparison matrices...
        //but we also have to reorder the lists themselves to contain the methods in that order so that we
        //conform to the restrictions imposed by the co-iteration of the lists during the analysis
        //the lists are already sorted in the natural order of the java elements which is first and foremost sorted
        //by element type (see org.revapi.java.model.JavaElementFactory). Let's exploit that and just remove all the
        //methods in the list and re-add them in the correct order.
        reAddSortedMethods(l1, l1MethodsInOrder);
        reAddSortedMethods(l2, l2MethodsInOrder);
    }

    private static void reAddSortedMethods(List<JavaElement> elements, List<JavaElement> sortedMethods) {
        int methodRank = JavaElementFactory.getModelTypeRank(MethodElement.class);
        int index = 0;
        for (; index < elements.size(); ++index) {
            JavaElement e = elements.get(index);
            if (JavaElementFactory.getModelTypeRank(e.getClass()) >= methodRank) {
                break;
            }
        }

        //remove all the method elements
        while (index < elements.size()) {
            JavaElement e = elements.get(index);
            if (e instanceof MethodElement) {
                elements.remove(index);
            } else {
                break;
            }
        }

        //and re-add them in the newly established order
        elements.addAll(index, sortedMethods);
    }

    private static List<String> methodParamsSignature(MethodElement method, boolean erased) {
        if (erased) {
            Types types = method.getTypeEnvironment().getTypeUtils();
            return method.getDeclaringElement().getParameters().stream().map(p ->
                    Util.toUniqueString(types.erasure(p.asType()))).collect(toList());
        } else {
            return method.getModelRepresentation().getParameterTypes().stream().map(Util::toUniqueString)
                    .collect(toList());
        }
    }

    private static int levenshteinDistance(String aRet, String aErasedRet, List<String> aParams,
            List<String> aErasedParams, MethodElement mb) {
        String bRet = Util.toUniqueString(mb.getModelRepresentation().getReturnType());
        String bErasedRet = Util.toUniqueString(mb.getTypeEnvironment().getTypeUtils()
                .erasure(mb.getModelRepresentation().getReturnType()));

        List<String> bParams = methodParamsSignature(mb, false);
        List<String> bErasedParams = methodParamsSignature(mb, true);

        int[][] d = new int[aParams.size() + 1][bParams.size() + 1];

        for (int i = 0; i < d.length; ++i) {
            d[i][0] = i;
        }

        for (int i = 0; i < d[0].length; ++i) {
            d[0][i] = i;
        }

        for (int i = 1; i < d.length; ++i) {
            for (int j = 1; j < d[0].length; ++j) {
                String a = aErasedParams.get(i - 1);
                String b = bErasedParams.get(j - 1);

                int cost = a.equals(b) ? 0 : 1;
                if (cost == 0) {
                    if (i == 1) {
                        a = aRet;
                    } else {
                        a = aParams.get(i - 2);
                    }

                    if (j == 1) {
                        b = bRet;
                    } else {
                        b = bParams.get(j - 2);
                    }

                    cost = a.equals(b) ? 0 : 1;
                }

                int min1 = d[i - 1][j] + 1;
                int min2 = d[i][j - 1] + 1;
                int min3 = d[i - 1][j - 1] + cost;
                d[i][j] = Math.min(Math.min(min1, min2), min3);
            }
        }

        // we need to make sure that a change in just the return type is classified as "closer" than any change in
        // the parameters. Let's just bump up the parameters distance by its maximum theoretical value (each param
        // different). This will make sure that a single parameter change is always considered worse than just a
        // return type change.
        int paramsDistance = d[d.length - 1][d[0].length - 1];
        if (paramsDistance > 0) {
            paramsDistance += bParams.size() * aParams.size();
        }

        //now compute the difference of the return types
        int retCost = aErasedRet.equals(bErasedRet) ? 0 : 1;
        if (retCost == 0) {
            retCost = aRet.equals(bRet) ? 0 : 1;
        }

        return retCost + paramsDistance;
    }

    private static int addAllMethods(Collection<? extends JavaElement> els, TreeMap<String,
            List<MethodElement>> methods) {

        int ret = 0;
        for (JavaElement e : els) {
            if (e instanceof MethodElement) {
                add((MethodElement) e, methods);
                ret++;
            }
        }

        return ret;
    }

    private static void add(MethodElement method, TreeMap<String, List<MethodElement>> methods) {
        String name = method.getDeclaringElement().getSimpleName().toString();
        List<MethodElement> overloads = methods.computeIfAbsent(name, __ -> new ArrayList<>());

        overloads.add(method);
    }

    @Override
    public String getExtensionId() {
        return "revapi.java";
    }

    @Override
    public Reader getJSONSchema() {
        Map<String, Reader> checkSchemas = new HashMap<>(4);
        for (Check c : checks) {
            String eid = c.getExtensionId();
            Reader schema = c.getJSONSchema();
            if (eid != null && schema != null) {
                checkSchemas.put(eid, schema);
            }
        }

        Reader rdr = new InputStreamReader(getClass().getResourceAsStream("/META-INF/config-schema.json"),
                StandardCharsets.UTF_8);

        if (checkSchemas.isEmpty()) {
            return rdr;
        } else {
            try {
                JsonNode baseSchema = JSONUtil.parse(consume(rdr));

                ObjectNode checksNode = baseSchema.with("properties").with("checks");
                checksNode.put("type", "object");

                for (Map.Entry<String, Reader> entry : checkSchemas.entrySet()) {
                    String checkId = entry.getKey();
                    Reader checkSchemaReader = entry.getValue();

                    JsonNode checkSchema = JSONUtil.parse(consume(checkSchemaReader));

                    checksNode.with("properties").set(checkId, checkSchema);
                }

                return new StringReader(baseSchema.toString());
            } catch (IOException e) {
                throw new IllegalStateException("Could not read the schema for the revapi extension...", e);
            }
        }
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        this.analysisContext = analysisContext;
        this.configuration = AnalysisConfiguration.fromModel(analysisContext.getConfigurationNode());

        configureExtensions("checks", checks);
        configureExtensions("extract", jarExtractors);
    }

    private void configureExtensions(String rootNode, Iterable<? extends Configurable> exts) {
        for (Configurable c : exts) {
            if (c.getExtensionId() != null) {
                JsonNode checkConfig = analysisContext.getConfigurationNode().path(rootNode).path(c.getExtensionId());
                AnalysisContext checkCtx = analysisContext.copyWithConfiguration(checkConfig);
                c.initialize(checkCtx);
            } else {
                c.initialize(analysisContext.copyWithConfiguration(JsonNodeFactory.instance.nullNode()));
            }
        }
    }

    @Nonnull
    @Override
    public JavaArchiveAnalyzer getArchiveAnalyzer(@Nonnull API api) {
        boolean ignoreMissingAnnotations = configuration.isIgnoreMissingAnnotations();
        return new JavaArchiveAnalyzer(this, api, jarExtractors, getExecutor(api), configuration.getMissingClassReporting(),
                ignoreMissingAnnotations, configuration.getPackageClassFilter());
    }

    @Nonnull
    @Override
    public DifferenceAnalyzer<JavaElement> getDifferenceAnalyzer(@Nonnull ArchiveAnalyzer<JavaElement> oldArchive,
            @Nonnull ArchiveAnalyzer<JavaElement> newArchive) {
        JavaArchiveAnalyzer oldA = (JavaArchiveAnalyzer) oldArchive;
        JavaArchiveAnalyzer newA = (JavaArchiveAnalyzer) newArchive;

        ProbingEnvironment oldEnvironment = oldA.getProbingEnvironment();
        ProbingEnvironment newEnvironment = newA.getProbingEnvironment();
        CompilationValve oldValve = oldA.getCompilationValve();
        CompilationValve newValve = newA.getCompilationValve();

        activeCompilations.add(oldValve);
        activeCompilations.add(newValve);

        return new JavaElementDifferenceAnalyzer(analysisContext, oldEnvironment, newEnvironment, checks,
                configuration);
    }

    @Override
    public void close() {
        compilationExecutors.forEach(ExecutorService::shutdown);

        activeCompilations.forEach(CompilationValve::removeCompiledResults);

        forceClearCompilerCache();
    }

    private static String consume(Reader rdr) throws IOException {
        Throwable suppressed = null;
        try {
            char[] buffer = new char[512];
            int cnt;
            StringBuilder bld = new StringBuilder();
            while ((cnt = rdr.read(buffer)) >= 0) {
                bld.append(buffer, 0, cnt);
            }

            return bld.toString();
        } catch (Throwable t) {
            suppressed = t;
            throw t;
        } finally {
            try {
                rdr.close();
            } catch (IOException e) {
                if (suppressed != null) {
                    e.addSuppressed(suppressed);
                }

                //noinspection ThrowFromFinallyBlock
                throw e;
            }
        }
    }

    //Javac's standard file manager prior to Java 9 is leaking resources across compilation tasks because it doesn't
    // clear a shared "zip file index" cache, when it is close()'d. We try to clear it by force.
    private static void forceClearCompilerCache() {
        if (CLEAR_COMPILER_CACHE != null && SHARED_ZIP_FILE_INDEX_CACHE != null) {
            try {
                CLEAR_COMPILER_CACHE.invoke(SHARED_ZIP_FILE_INDEX_CACHE);
            } catch (IllegalAccessException | InvocationTargetException e) {
                LOG.warn("Failed to force-clear compiler caches, even though it should have been possible." +
                        "This will probably leak memory", e);
            }
        }
    }

    private ExecutorService getExecutor(API api) {
        ExecutorService ret = Executors.newSingleThreadExecutor(r -> {
            String as = StreamSupport.stream(api.getArchives().spliterator(), false)
                    .map(Archive::getName).collect(Collectors.joining(", "));
            Thread t = new Thread(r, "Java API Compilation Thread for API of " + as);
            t.setDaemon(true);
            return t;
        });

        compilationExecutors.add(ret);
        return ret;

    }
}
