/*
 * This file is part of FastClasspathScanner.
 *
 * Author: Luke Hutchison
 *
 * Hosted at: https://github.com/lukehutch/fast-classpath-scanner
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 Luke Hutchison
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.github.lukehutch.fastclasspathscanner;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.lukehutch.fastclasspathscanner.utils.JarUtils;
import io.github.lukehutch.fastclasspathscanner.utils.ReflectionUtils;

/** A ModuleReference proxy, written using reflection to preserve backwards compatibility with JDK 7 and 8. */
public class ModuleRef implements Comparable<ModuleRef> {
    /** The name of the module. */
    private final String moduleName;

    /** The ModuleReference for the module. */
    private final Object moduleReference;

    /** The ModuleLayer for the module. */
    private final Object moduleLayer;

    /** The ModuleDescriptor for the module. */
    private final Object moduleDescriptor;

    /** The packages in the module. */
    private final List<String> modulePackages;

    /** The location URI for the module (may be null). */
    private final URI moduleLocation;

    /** A file formed from the location URI. The file will not exist if the location URI is a jrt:/ URI. */
    private File moduleLocationFile;

    /** The location URI for the module, as a cached string (may be null). */
    private String moduleLocationStr;

    /** The ClassLoader that loads classes in the module. May be null, to represent the bootstrap classloader. */
    private final ClassLoader classLoader;

    ModuleRef(final Object moduleReference, final Object moduleLayer) {
        if (moduleReference == null) {
            throw new IllegalArgumentException("moduleReference cannot be null");
        }
        if (moduleLayer == null) {
            throw new IllegalArgumentException("moduleLayer cannot be null");
        }
        this.moduleReference = moduleReference;
        this.moduleLayer = moduleLayer;

        moduleDescriptor = ReflectionUtils.invokeMethod(moduleReference, "descriptor", /* throwException = */ true);
        if (moduleDescriptor == null) {
            // Should not happen
            throw new IllegalArgumentException("moduleReference.descriptor() should not return null");
        }
        moduleName = (String) ReflectionUtils.invokeMethod(moduleDescriptor, "name", /* throwException = */ true);
        if (moduleName == null) {
            // Should not happen
            throw new IllegalArgumentException("moduleReference.descriptor().name() should not return null");
        }
        @SuppressWarnings("unchecked")
        final Set<String> pkgs = (Set<String>) ReflectionUtils.invokeMethod(moduleDescriptor, "packages",
                /* throwException = */ true);
        if (pkgs == null) {
            // Should not happen
            throw new IllegalArgumentException("moduleReference.descriptor().packages() should not return null");
        }
        modulePackages = new ArrayList<>(pkgs);
        Collections.sort(modulePackages);
        final Object moduleLocationOptional = ReflectionUtils.invokeMethod(moduleReference, "location",
                /* throwException = */ true);
        if (moduleLocationOptional == null) {
            // Should not happen
            throw new IllegalArgumentException("moduleReference.location() should not return null");
        }
        final Object moduleLocationIsPresent = ReflectionUtils.invokeMethod(moduleLocationOptional, "isPresent",
                /* throwException = */ true);
        if (moduleLocationIsPresent == null) {
            // Should not happen
            throw new IllegalArgumentException("moduleReference.location().isPresent() should not return null");
        }
        if ((Boolean) moduleLocationIsPresent) {
            moduleLocation = (URI) ReflectionUtils.invokeMethod(moduleLocationOptional, "get",
                    /* throwException = */ true);
            if (moduleLocation == null) {
                // Should not happen
                throw new IllegalArgumentException("moduleReference.location().get() should not return null");
            }
        } else {
            moduleLocation = null;
        }

        // Find the classloader for the module
        classLoader = (ClassLoader) ReflectionUtils.invokeMethod(moduleLayer, "findLoader", String.class,
                moduleName, /* throwException = */ true);
    }

    /** Returns the module name, i.e. {@code moduleReference.descriptor().name()}. */
    public String getModuleName() {
        return moduleName;
    }

    /** Returns the module reference (type ModuleReference). */
    public Object getModuleReference() {
        return moduleReference;
    }

    /** Returns the module layer (type ModuleLayer). */
    public Object getModuleLayer() {
        return moduleLayer;
    }

    /** Returns the module descriptor, i.e. {@code moduleReference.descriptor()} (type ModuleDescriptor). */
    public Object getModuleDescriptor() {
        return moduleDescriptor;
    }

    /** Returns the list of packages in the module. */
    public List<String> getModulePackages() {
        return modulePackages;
    }

    /**
     * Returns the module location, i.e. {@code moduleReference.location()}. Returns null for modules that do not
     * have a location.
     */
    public URI getModuleLocation() {
        return moduleLocation;
    }

    /**
     * Returns true if this module's location is a non-"file:/" ("jrt:/") URI, or if it has no location URI, or if
     * it uses the (null) bootstrap ClassLoader, or if the module name starts with a system prefix ("java.", "jre.",
     * etc.).
     */
    public boolean isSystemModule() {
        if (moduleLocation == null || classLoader == null) {
            return true;
        }
        if (JarUtils.isInSystemPackageOrModule(moduleName)) {
            return true;
        }
        final String scheme = moduleLocation.getScheme();
        if (scheme == null) {
            return false;
        }
        return !scheme.equalsIgnoreCase("file");
    }

    /**
     * Returns the module location as a string, i.e. {@code moduleReference.location().toString()}. Returns null for
     * modules that do not have a location.
     */
    public String getModuleLocationStr() {
        if (moduleLocationStr == null && moduleLocation != null) {
            moduleLocationStr = moduleLocation.toString();
        }
        return moduleLocationStr;
    }

    /**
     * Returns the module location as a File, i.e. {@code new File(moduleReference.location())}. Returns null for
     * modules that do not have a location, or for system ("jrt:/") modules.
     */
    public File getModuleLocationFile() {
        if (moduleLocationFile == null && moduleLocation != null) {
            if (!isSystemModule()) {
                try {
                    moduleLocationFile = new File(moduleLocation);
                } catch (final Exception e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return moduleLocationFile;
    }

    /**
     * Returns the classloader for the module, i.e.
     * {@code moduleLayer.findLoader(moduleReference.descriptor().name())}.
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof ModuleRef)) {
            return false;
        }
        final ModuleRef mr = (ModuleRef) obj;
        return moduleReference.equals(this.moduleReference) && mr.moduleLayer.equals(this.moduleLayer);
    }

    @Override
    public int hashCode() {
        return moduleReference.hashCode() * moduleLayer.hashCode();
    }

    @Override
    public String toString() {
        return moduleReference.toString() + "; ClassLoader " + classLoader;
    }

    @Override
    public int compareTo(final ModuleRef o) {
        final int diff = this.moduleName.compareTo(o.moduleName);
        return diff != 0 ? diff : this.hashCode() - o.hashCode();
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Open the module, returning a {@link ModuleReaderProxy}. */
    public ModuleReaderProxy open() throws IOException {
        return new ModuleReaderProxy(this);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Recursively find the topological sort order of ancestral layers. The JDK (as of 10.0.0.1) uses a broken
     * (non-topological) DFS ordering for layer resolution in ModuleLayer#layers() and
     * Configuration#configurations() but when I reported this bug on the Jigsaw mailing list, Alan didn't see what
     * the problem was...
     */
    private static void findLayerOrder(final Object /* ModuleLayer */ layer,
            final Set<Object> /* Set<ModuleLayer> */ visited,
            final Deque<Object> /* Deque<ModuleLayer> */ layersOut) {
        if (visited.add(layer)) {
            @SuppressWarnings("unchecked")
            final List<Object> /* List<ModuleLayer> */ parents = (List<Object>) ReflectionUtils.invokeMethod(layer,
                    "parents", /* throwException = */ true);
            if (parents != null) {
                for (int i = 0; i < parents.size(); i++) {
                    findLayerOrder(parents.get(i), visited, layersOut);
                }
            }
            layersOut.push(layer);
        }
    }

    /**
     * Get all visible ModuleReferences in all layers, given an array of stack frame {@code Class<?>} references.
     */
    public static List<ModuleRef> findModuleRefs(final Class<?>[] callStack) {
        Deque<Object> /* Deque<ModuleLayer> */ layers = null;
        final HashSet<Object> /* HashSet<ModuleLayer> */ visited = new HashSet<>();
        for (int i = 0; i < callStack.length; i++) {
            final Object /* Module */ module = ReflectionUtils.invokeMethod(callStack[i], "getModule",
                    /* throwException = */ false);
            if (module != null) {
                final Object /* ModuleLayer */ layer = ReflectionUtils.invokeMethod(module, "getLayer",
                        /* throwException = */ true);
                if (layer != null) {
                    if (layers == null) {
                        layers = new ArrayDeque<>();
                    }
                    findLayerOrder(layer, visited, layers);
                }
            }
        }
        if (layers != null) {
            // Find modules in the ordered layers
            final LinkedHashSet<ModuleRef> moduleRefs = new LinkedHashSet<>();
            for (final Object /* ModuleLayer */ layer : layers) {
                final Object /* Configuration */ cfg = ReflectionUtils.invokeMethod(layer, "configuration",
                        /* throwException = */ true);
                if (cfg != null) {
                    // Get ModuleReferences from layer configuration
                    @SuppressWarnings("unchecked")
                    final Set<Object> /* Set<ResolvedModule> */ modules = (Set<Object>) ReflectionUtils
                            .invokeMethod(cfg, "modules", /* throwException = */ true);
                    if (modules != null) {
                        final List<ModuleRef> modulesInLayer = new ArrayList<>();
                        for (final Object /* ResolvedModule */ module : modules) {
                            final Object /* ModuleReference */ ref = ReflectionUtils.invokeMethod(module,
                                    "reference", /* throwException = */ true);
                            if (ref != null) {
                                modulesInLayer.add(new ModuleRef(ref, layer));
                            }
                        }
                        // Sort modules in layer by name
                        Collections.sort(modulesInLayer);
                        moduleRefs.addAll(modulesInLayer);
                    }
                }
            }
            return new ArrayList<>(moduleRefs);
        } else {
            return Collections.<ModuleRef> emptyList();
        }
    }
}
