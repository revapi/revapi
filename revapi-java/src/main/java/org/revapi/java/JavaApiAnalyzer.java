/*
 * Copyright 2014-2019 Lukas Krejci
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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.util.Types;
import javax.tools.ToolProvider;

import org.jboss.dmr.ModelNode;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.ApiAnalyzer;
import org.revapi.Archive;
import org.revapi.ArchiveAnalyzer;
import org.revapi.CoIterator;
import org.revapi.CorrespondenceComparatorDeducer;
import org.revapi.DifferenceAnalyzer;
import org.revapi.Element;
import org.revapi.java.compilation.CompilationValve;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.model.JavaElementFactory;
import org.revapi.java.model.MethodElement;
import org.revapi.java.model.TypeElement;
import org.revapi.java.spi.Check;
import org.revapi.java.spi.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class JavaApiAnalyzer implements ApiAnalyzer {
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
    private final List<CompilationValve> activeCompilations = new ArrayList<>(2);

    public JavaApiAnalyzer() {
        this(ServiceLoader.load(Check.class, JavaApiAnalyzer.class.getClassLoader()));
    }

    public JavaApiAnalyzer(Iterable<Check> checks) {
        this.checks = checks;
    }

    @Override
    @Nonnull
    public CorrespondenceComparatorDeducer getCorrespondenceDeducer() {
        return (l1, l2) -> {
            //so, we have to come up with some correspondence order... This is pretty easy for all java elements
            //but methods.

            if (l1.isEmpty() || l2.isEmpty()) {
                return Comparator.naturalOrder();
            }

            //quickly peek inside to see if there even can be methods in the lists - all of the elements in either list
            //will have a common parent and parents of both lists will have the same type or be both null.
            Element parent = l1.get(0).getParent();
            if (!(parent instanceof TypeElement)) {
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

    private static void determineOrder(List<Element> l1, List<Element> l2,
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
                (e1, e2) -> e1.getKey().compareTo(e2.getKey()));

        List<Element> l2MethodsInOrder = new ArrayList<>(l1MethodsSize);
        List<Element> l1MethodsInOrder = new ArrayList<>(l2MethodsSize);

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
                    List<Element> aio = l1MethodsInOrder;
                    List<Element> bio = l2MethodsInOrder;
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

                    for (MethodElement aMethod : as) {
                        ao.put(aMethod, index);
                        aio.add(aMethod);

                        MethodElement bMethod = removeBestMatch(aMethod, bs);
                        bo.put(bMethod, index++);
                        bio.add(bMethod);
                    }

                    //add the rest
                    for (MethodElement m : bs) {
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

    private static void reAddSortedMethods(List<Element> elements, List<Element> sortedMethods) {
        int methodRank = JavaElementFactory.getModelTypeRank(MethodElement.class);
        int index = 0;
        for (; index < elements.size(); ++index) {
            Element e = elements.get(index);
            if (JavaElementFactory.getModelTypeRank(e.getClass()) >= methodRank) {
                break;
            }
        }

        //remove all the method elements
        while (index < elements.size()) {
            Element e = elements.get(index);
            if (e instanceof MethodElement) {
                elements.remove(index);
            } else {
                break;
            }
        }

        //and re-add them in the newly established order
        elements.addAll(index, sortedMethods);
    }

    private static MethodElement removeBestMatch(MethodElement blueprint, List<MethodElement> candidates) {
        MethodElement best = null;
        float maxScore = 0;
        int bestIdx = -1;

        List<String> fullBlueprintSignature = methodParamsSignature(blueprint, false);
        List<String> erasedBlueprintSignature = methodParamsSignature(blueprint, true);

        String fullBlueprintReturnType = Util.toUniqueString(blueprint.getModelRepresentation().getReturnType());
        String erasedBlueprintReturnType = Util.toUniqueString(blueprint.getTypeEnvironment().getTypeUtils()
                .erasure(blueprint.getModelRepresentation().getReturnType()));

        int idx = 0;
        for (MethodElement candidate : candidates) {
            float score = computeMatchScore(fullBlueprintReturnType, fullBlueprintSignature,
                    erasedBlueprintReturnType, erasedBlueprintSignature, candidate);
            if (maxScore <= score) {
                best = candidate;
                maxScore = score;
                bestIdx = idx;
            }
            idx++;
        }

        if (bestIdx != -1) {
            candidates.remove(bestIdx);
        }

        return best;
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

    private static float computeMatchScore(String blueprintReturnType, List<String> blueprintParamSignature,
            String erasedReturnType, List<String> erasedParamSignature, MethodElement method) {

        String mRt = Util.toUniqueString(method.getModelRepresentation().getReturnType());
        String emRt = Util.toUniqueString(method.getTypeEnvironment().getTypeUtils()
                .erasure(method.getModelRepresentation().getReturnType()));

        List<String> mPs = methodParamsSignature(method, false);
        List<String> emPs = methodParamsSignature(method, true);


        //consider the return type as if it was another parameter
        int maxParams = Math.max(blueprintParamSignature.size(), mPs.size()) + 1;

        int commonParams = longestCommonSubsequenceLength(blueprintParamSignature, mPs,
                (blueprintIndex, methodIndex) -> {

                    String fullBlueprintSig = blueprintParamSignature.get(blueprintIndex);
                    String erasedBlueprintSig = erasedParamSignature.get(blueprintIndex);

                    String fullMethodSig = mPs.get(methodIndex);
                    String erasedMethodSig = emPs.get(methodIndex);

                    if (fullBlueprintSig.equals(fullMethodSig)) {
                        return 2;
                    } else if (erasedBlueprintSig.equals(erasedMethodSig)) {
                        return 1;
                    } else {
                        return 0;
                    }
                });

        //consider the return type as if it was another matching parameter
        if (blueprintReturnType.equals(mRt)) {
            commonParams += 2;
        } else if (erasedReturnType.equals(emRt)) {
            commonParams += 1;
        }

        if (maxParams == 1) {
            //both methods have no parameters
            //we consider that fact a "complete match"
            return commonParams + 2;
        } else {
            //just consider the return type as one of parameters
            return ((float) commonParams) / maxParams;
        }
    }

    private static int longestCommonSubsequenceLength(List<?> as, List<?> bs, BiFunction<Integer, Integer, Integer>
            matchScoreFunction) {
        int[][] lengths = new int[as.size() + 1][bs.size() + 1];
        int maxLen = 0;
        // row 0 and column 0 are initialized to 0 already
        for (int i = 0; i < as.size(); i++) {
            for (int j = 0; j < bs.size(); j++) {
                int matchScore = matchScoreFunction.apply(i, j);

                if (matchScore > 0) {
                    maxLen = lengths[i + 1][j + 1] = lengths[i][j] + matchScore;
                } else {
                    lengths[i + 1][j + 1] =
                            Math.max(lengths[i + 1][j], lengths[i][j + 1]);
                }
            }
        }

        return maxLen;
    }

    private static int addAllMethods(Collection<? extends Element> els, TreeMap<String,
            List<MethodElement>> methods) {

        int ret = 0;
        for (Element e : els) {
            if (e instanceof MethodElement) {
                add((MethodElement) e, methods);
                ret++;
            }
        }

        return ret;
    }

    private static void add(MethodElement method, TreeMap<String, List<MethodElement>> methods) {
        String name = method.getDeclaringElement().getSimpleName().toString();
        List<MethodElement> overloads = methods.get(name);
        if (overloads == null) {
            overloads = new ArrayList<>();
            methods.put(name, overloads);
        }

        overloads.add(method);
    }

    @Nullable
    @Override
    public String getExtensionId() {
        return "revapi.java";
    }

    @Nullable
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
                Charset.forName("UTF-8"));

        if (checkSchemas.isEmpty()) {
            return rdr;
        } else {
            try {
                ModelNode baseSchema = ModelNode.fromJSONString(consume(rdr));

                ModelNode checksNode = baseSchema.get("properties", "checks");
                checksNode.get("type").set("object");

                for (Map.Entry<String, Reader> entry : checkSchemas.entrySet()) {
                    String checkId = entry.getKey();
                    Reader checkSchemaReader = entry.getValue();

                    ModelNode checkSchema = ModelNode.fromJSONString(consume(checkSchemaReader));

                    checksNode.get("properties").get(checkId).set(checkSchema);
                }

                return new StringReader(baseSchema.toJSONString(false));
            } catch (IOException e) {
                throw new IllegalStateException("Could not read the schema for the revapi extension...", e);
            }
        }
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        this.analysisContext = analysisContext;
        this.configuration = AnalysisConfiguration.fromModel(analysisContext.getConfiguration());

        for (Check c : checks) {
            if (c.getExtensionId() != null) {
                ModelNode checkConfig = analysisContext.getConfiguration().get("checks", c.getExtensionId());
                AnalysisContext checkCtx = analysisContext.copyWithConfiguration(checkConfig);
                c.initialize(checkCtx);
            } else {
                c.initialize(analysisContext.copyWithConfiguration(new ModelNode()));
            }
        }
    }

    @Nonnull
    @Override
    public JavaArchiveAnalyzer getArchiveAnalyzer(@Nonnull API api) {
        boolean ignoreMissingAnnotations = configuration.isIgnoreMissingAnnotations();
        return new JavaArchiveAnalyzer(api, getExecutor(api), configuration.getMissingClassReporting(),
                ignoreMissingAnnotations);
    }

    @Nonnull
    @Override
    public DifferenceAnalyzer getDifferenceAnalyzer(@Nonnull ArchiveAnalyzer oldArchive,
            @Nonnull ArchiveAnalyzer newArchive) {
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
            return new Thread(r, "Java API Compilation Thread for API of " + as);
        });

        compilationExecutors.add(ret);
        return ret;

    }
}
