/*
 * Copyright 2015 Lukas Krejci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.revapi.java;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.util.Types;

import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.ApiAnalyzer;
import org.revapi.ArchiveAnalyzer;
import org.revapi.CoIterator;
import org.revapi.CorrespondenceComparatorDeducer;
import org.revapi.DifferenceAnalyzer;
import org.revapi.Element;
import org.revapi.java.compilation.CompilationValve;
import org.revapi.java.compilation.InclusionFilter;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.model.JavaElementFactory;
import org.revapi.java.model.MethodElement;
import org.revapi.java.model.TypeElement;
import org.revapi.java.spi.Check;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class JavaApiAnalyzer implements ApiAnalyzer {

    private final ExecutorService compilationExecutor = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private volatile int cnt;

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "Java API Compilation Thread #" + (++cnt));
        }
    });

    private AnalysisContext analysisContext;
    private AnalysisConfiguration configuration;
    private final Iterable<Check> checks;

    public JavaApiAnalyzer() {
        this(ServiceLoader.load(Check.class, JavaApiAnalyzer.class.getClassLoader()));
    }

    public JavaApiAnalyzer(Iterable<Check> checks) {
        this.checks = checks;
    }

    @Override
    public @Nonnull CorrespondenceComparatorDeducer getCorrespondenceDeducer() {
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

        addAllMethods(l1, l1MethodsByName);
        addAllMethods(l2, l2MethodsByName);

        //rehash overloads that are present in both collections - those are then reordered using their mutual
        //resemblance

        int index = 0;
        Iterator<Map.Entry<String, List<MethodElement>>> l1MethodsIterator = l1MethodsByName.entrySet().iterator();
        Iterator<Map.Entry<String, List<MethodElement>>> l2MethodsIterator = l2MethodsByName.entrySet().iterator();

        //iterate over the maps, sorted by name and assign the comparison index to the methods.
        //we iterate over the maps sorted by method name
        CoIterator<Map.Entry<String, List<MethodElement>>> coit = new CoIterator<>(l1MethodsIterator, l2MethodsIterator,
                (e1, e2) -> e1.getKey().compareTo(e2.getKey()));

        List<Element> l2MethodsInOrder = new ArrayList<>();

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

                    l2MethodsInOrder.add(m2);
                    l2MethodOrder.put(m2, index);
                    l1MethodOrder.put(m1, index++);
                } else {
                    //slow path - for each overload in l1, we need to pick the appropriate one from l2 and put it in the
                    //same place
                    for (MethodElement c1Method : l1Overloads) {
                        MethodElement c2Method = removeBestMatch(c1Method, l2Overloads);
                        if (c2Method != null) {
                            l2MethodOrder.put(c2Method, index);
                            l2MethodsInOrder.add(c2Method);
                        }
                        l1MethodOrder.put(c1Method, index++);
                    }

                    //add the rest
                    for (MethodElement m : l2Overloads) {
                        l2MethodOrder.put(m, index++);
                        l2MethodsInOrder.add(m);
                    }
                }
            }
        }

        //ok, so now we have the method indices right in the comparison matrices...
        //but we also have to reorder the lists themselves to contain the methods in that order so that we
        //conform to the restrictions imposed by the co-iteration of the lists during the analysis
        //the lists are already sorted in the natural order of the java elements which is first and foremost sorted
        //by element type (see org.revapi.java.model.JavaElementFactory). Let's exploit that and just remove all the
        //methods in the list and re-add them in the correct order. This is actually only necessary in the second list
        //because the methods in the first list don't change their order.

        //find the index to add the methods to - exploit the sorting by element type
        int methodRank = JavaElementFactory.getModelTypeRank(MethodElement.class);
        index = 0;
        for (; index < l2.size(); ++index) {
            Element e = l2.get(index);
            if (JavaElementFactory.getModelTypeRank(e.getClass()) >= methodRank) {
                break;
            }
        }

        //remove all the method elements
        while (index < l2.size()) {
            Element e = l2.get(index);
            if (e instanceof MethodElement) {
                l2.remove(index);
            } else {
                break;
            }
        }

        //and re-add them in the newly established order
        l2.addAll(index, l2MethodsInOrder);
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

    private static void addAllMethods(Collection<? extends Element> els, TreeMap<String,
            List<MethodElement>> methods) {

        els.forEach(e -> {
            if (e instanceof MethodElement) {
                add((MethodElement) e, methods);
            }
        });
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
    public String[] getConfigurationRootPaths() {
        ArrayList<String> checkConfigPaths = new ArrayList<>();
        checkConfigPaths.add("revapi.java");

        for (Check c : checks) {
            String[] cp = c.getConfigurationRootPaths();
            if (cp != null) {
                checkConfigPaths.addAll(Arrays.asList(cp));
            }
        }

        String[] configs = new String[checkConfigPaths.size()];
        configs = checkConfigPaths.toArray(configs);

        return configs;
    }

    @Nullable
    @Override
    public Reader getJSONSchema(@Nonnull String configurationRootPath) {
        if ("revapi.java".equals(configurationRootPath)) {
            return new InputStreamReader(getClass().getResourceAsStream("/META-INF/config-schema.json"),
                    Charset.forName("UTF-8"));
        }

        for (Check check : checks) {
            String[] roots = check.getConfigurationRootPaths();
            if (roots == null) {
                continue;
            }

            for (String root : check.getConfigurationRootPaths()) {
                if (configurationRootPath.equals(root)) {
                    return check.getJSONSchema(root);
                }
            }
        }

        return null;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        this.analysisContext = analysisContext;
        this.configuration = AnalysisConfiguration.fromModel(analysisContext.getConfiguration());
    }

    @Nonnull
    @Override
    public ArchiveAnalyzer getArchiveAnalyzer(@Nonnull API api) {
        Set<File> bootstrapClasspath =
                api == analysisContext.getOldApi() ? configuration.getOldApiBootstrapClasspath() :
                        configuration.getNewApiBootstrapClasspath();
        boolean ignoreMissingAnnotations = configuration.isIgnoreMissingAnnotations();
        boolean skipUseTracking = !configuration.isDeepUseChainAnalysis();
        InclusionFilter inclusionFilter = composeInclusionFilter(configuration);

        return new JavaArchiveAnalyzer(api, compilationExecutor, configuration.getMissingClassReporting(),
                ignoreMissingAnnotations, skipUseTracking, bootstrapClasspath, inclusionFilter);
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

        return new JavaElementDifferenceAnalyzer(analysisContext, oldEnvironment, oldValve, newEnvironment, newValve,
                checks, configuration);
    }

    @Override
    public void close() {
        compilationExecutor.shutdown();
    }

    private static InclusionFilter composeInclusionFilter(AnalysisConfiguration config) {
        final Set<Pattern> inclClasses = config.getClassInclusionFilters();
        final Set<Pattern> exclClasses = config.getClassExclusionFilters();
        final Set<Pattern> inclPkgs = config.getPackageInclusionFilters();
        final Set<Pattern> exclPkgs = config.getPackageExclusionFilters();

        return new InclusionFilter() {
            @Override
            public boolean accepts(String typeBinaryName, String typeCanonicalName) {
                for (Pattern p : inclClasses) {
                    if (p.matcher(typeCanonicalName).matches()) {
                        return true;
                    }
                }

                int lastDot = typeBinaryName.lastIndexOf('.');
                String pkg = lastDot == -1 ? "" : typeBinaryName.substring(0, lastDot);


                for (Pattern p : inclPkgs) {
                    if (p.matcher(pkg).matches()) {
                        return true;
                    }
                }

                return false;
            }

            @Override
            public boolean rejects(String typeBinaryName, String typeCanonicalName) {
                for (Pattern p : exclClasses) {
                    if (p.matcher(typeCanonicalName).matches()) {
                        return true;
                    }
                }

                int lastDot = typeBinaryName.lastIndexOf('.');
                String pkg = lastDot == -1 ? "" : typeBinaryName.substring(0, lastDot);

                for (Pattern p : exclPkgs) {
                    if (p.matcher(pkg).matches()) {
                        return true;
                    }
                }

                return false;
            }

            @Override
            public boolean defaultCase() {
                return inclClasses.isEmpty() && inclPkgs.isEmpty();
            }
        };
    }
}
