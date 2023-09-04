/*
 * Copyright 2014-2023 Lukas Krejci
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
package org.revapi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;

/**
 * Because Revapi manages the lifecycle of instances of its extensions, it cannot unfortunately use the
 * {@link java.util.ServiceLoader} directly (before Java 9 which enhances it to support the use case Revapi needs).
 *
 * <p>
 * This class is similar to {@link java.util.ServiceLoader} but instead of providing instances of the service
 * implementations, it provides <b>types</b> of the service implementations. Users of this class are then responsible
 * for using these types anyway they want (instantiate them or whatever).
 *
 * @author Lukas Krejci
 *
 * @since 0.8.0
 */
public final class ServiceTypeLoader<T> implements Iterable<Class<? extends T>> {
    private static final String PREFIX = "META-INF/services/";

    private final ClassLoader classLoader;
    private final Class<T> serviceType;
    private List<Class<? extends T>> cache;

    private ServiceTypeLoader(ClassLoader classLoader, Class<T> serviceType) {
        this.classLoader = classLoader;
        this.serviceType = serviceType;
    }

    public static <X> ServiceTypeLoader<X> load(Class<X> serviceType, ClassLoader cl) {
        return new ServiceTypeLoader<>(cl, serviceType);
    }

    /**
     * Locates the services in the context classloader of the current thread.
     *
     * @param serviceType
     *            the type of the services to locate
     * @param <X>
     *            the type of the service
     *
     * @return the service type loader
     */
    public static <X> ServiceTypeLoader<X> load(Class<X> serviceType) {
        return load(serviceType, Thread.currentThread().getContextClassLoader());
    }

    public void reload() {
        cache = null;
    }

    @Override
    public Iterator<Class<? extends T>> iterator() {
        if (cache == null) {
            load();
        }

        return cache.iterator();
    }

    @SuppressWarnings("unchecked")
    private void load() throws ServiceConfigurationError {
        cache = new ArrayList<>();

        String serviceFileLocation = PREFIX + serviceType.getName();
        Enumeration<URL> resources;

        try {
            resources = classLoader == null ? ClassLoader.getSystemResources(serviceFileLocation)
                    : classLoader.getResources(serviceFileLocation);
        } catch (IOException e) {
            throw new ServiceConfigurationError(serviceType.getName() + ": Failed to load service configuration file.");
        }

        while (resources.hasMoreElements()) {
            URL resourceFile = resources.nextElement();

            String line = null;
            int lineNum = 0;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(resourceFile.openStream()))) {
                while ((line = in.readLine()) != null) {
                    lineNum++;

                    int hashIdx = line.indexOf('#');

                    if (hashIdx >= 0) {
                        line = line.substring(0, hashIdx);
                    }

                    line = line.trim();

                    if (line.isEmpty()) {
                        continue;
                    }

                    cache.add((Class<? extends T>) Class.forName(line, false, classLoader));
                }
            } catch (IOException e) {
                throw new ServiceConfigurationError(
                        serviceType.getName() + ": " + resourceFile + "Failed to read service configuration file.", e);
            } catch (ClassNotFoundException e) {
                throw new ServiceConfigurationError(
                        serviceType.getName() + ": " + resourceFile + ":" + lineNum + ": Class not found: " + line, e);
            } catch (ClassCastException e) {
                throw new ServiceConfigurationError(serviceType.getName() + ": " + resourceFile + ":" + lineNum
                        + ": Class doesn't implement service interface: " + line, e);
            }
        }
    }
}
