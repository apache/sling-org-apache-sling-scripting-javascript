/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.scripting.javascript.internal;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.scripting.api.AbstractScriptEngineFactory;
import org.apache.sling.scripting.api.ScriptCache;
import org.apache.sling.scripting.javascript.RhinoHostObjectProvider;
import org.apache.sling.scripting.javascript.SlingWrapper;
import org.apache.sling.scripting.javascript.helper.SlingContextFactory;
import org.apache.sling.scripting.javascript.helper.SlingWrapFactory;
import org.apache.sling.scripting.javascript.wrapper.ScriptableCalendar;
import org.apache.sling.scripting.javascript.wrapper.ScriptableItemMap;
import org.apache.sling.scripting.javascript.wrapper.ScriptableMap;
import org.apache.sling.scripting.javascript.wrapper.ScriptableNode;
import org.apache.sling.scripting.javascript.wrapper.ScriptablePrintWriter;
import org.apache.sling.scripting.javascript.wrapper.ScriptableProperty;
import org.apache.sling.scripting.javascript.wrapper.ScriptableResource;
import org.apache.sling.scripting.javascript.wrapper.ScriptableVersion;
import org.apache.sling.scripting.javascript.wrapper.ScriptableVersionHistory;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.NativeJavaClass;
import org.mozilla.javascript.NativeJavaPackage;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.tools.debugger.ScopeProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        service = ScriptEngineFactory.class,
        property = {
            Constants.SERVICE_DESCRIPTION + "=Apache Sling Rhino Javascript Engine Factory",
            Constants.SERVICE_VENDOR + "=The Apache Software Foundation",
            "extensions=" + RhinoJavaScriptEngineFactory.ECMA_SCRIPT_EXTENSION,
            "extensions=" + RhinoJavaScriptEngineFactory.ESP_SCRIPT_EXTENSION,
            "mimeTypes=text/ecmascript",
            "mimeTypes=text/javascript",
            "mimeTypes=application/ecmascript",
            "mimeTypes=application/javascript",
            "names=rhino",
            "names=Rhino",
            "names=javascript",
            "names=JavaScript",
            "names=ecmascript",
            "names=ECMAScript"
        },
        reference =
                @Reference(
                        name = "HostObjectProvider",
                        service = RhinoHostObjectProvider.class,
                        cardinality = ReferenceCardinality.MULTIPLE,
                        policy = ReferencePolicy.DYNAMIC,
                        bind = "addHostObjectProvider",
                        unbind = "removeHostObjectProvider"))
@Designate(ocd = RhinoJavaScriptEngineFactoryConfiguration.class)
public class RhinoJavaScriptEngineFactory extends AbstractScriptEngineFactory implements ScopeProvider {

    public static final int DEFAULT_OPTIMIZATION_LEVEL = 9;

    public static final String ECMA_SCRIPT_EXTENSION = "ecma";

    public static final String ESP_SCRIPT_EXTENSION = "esp";

    private static final Class<?>[] HOSTOBJECT_CLASSES = {
        ScriptableResource.class,
        ScriptableNode.class,
        ScriptableProperty.class,
        ScriptableItemMap.class,
        ScriptablePrintWriter.class,
        ScriptableVersionHistory.class,
        ScriptableVersion.class,
        ScriptableCalendar.class,
        ScriptableMap.class
    };

    /**
     * default log
     */
    private final Logger log = LoggerFactory.getLogger(getClass());

    private int optimizationLevel;

    public static final int RHINO_LANGUAGE_VERSION = Context.VERSION_ES6;
    private static final String LANGUAGE_VERSION = "partial ECMAScript 2015 support";
    private static final String LANGUAGE_NAME = "ECMAScript";

    private SlingWrapFactory wrapFactory;

    private Scriptable rootScope;

    private final Set<RhinoHostObjectProvider> hostObjectProvider = new HashSet<RhinoHostObjectProvider>();

    private SlingContextFactory contextFactory;

    @Reference
    private DynamicClassLoaderManager dynamicClassLoaderManager = null;

    @Reference
    private ScriptCache scriptCache = null;

    // SLING-9792
    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock readLock = rwl.readLock();
    private final Lock writeLock = rwl.writeLock();
    private volatile boolean active = false;

    public ScriptEngine getScriptEngine() {
        ScriptEngine se = null;
        readLock.lock();
        try {
            if (active) {
                se = new RhinoJavaScriptEngine(this, getRootScope(), scriptCache);
            } else {
                log.warn("getScriptEngine() called, but this component is not active.");
            }
        } finally {
            readLock.unlock();
        }

        return se;
    }

    public String getLanguageName() {
        return LANGUAGE_NAME;
    }

    public String getLanguageVersion() {
        return LANGUAGE_VERSION;
    }

    /**
     * Get the optimization level that should be used when running JS scripts
     * with Rhino
     *
     * @return an integer from 0-9 with 9 being the most aggressive optimization, or
     * -1 if interpreted mode is to be used
     */
    int getOptimizationLevel() {
        return optimizationLevel;
    }

    int rhinoLanguageVersion() {
        return Context.VERSION_ES6;
    }

    public Object getParameter(String name) {
        if ("THREADING".equals(name)) {
            return "MULTITHREADED";
        }

        return super.getParameter(name);
    }

    public Scriptable getScope() {
        Scriptable scope = null;
        readLock.lock();
        try {
            if (active) {
                scope = getRootScope();
            } else {
                log.warn("getScope() called, but this component is not active.");
            }
        } finally {
            readLock.unlock();
        }

        return scope;
    }

    SlingWrapFactory getWrapFactory() {
        return wrapFactory;
    }

    @SuppressWarnings("unchecked")
    private Scriptable getRootScope() {
        if (rootScope == null) {

            final Context rhinoContext = Context.enter();
            try {
                rhinoContext.setOptimizationLevel(optimizationLevel);
                rhinoContext.setLanguageVersion(RHINO_LANGUAGE_VERSION);
                Scriptable tmpScope = rhinoContext.initStandardObjects(new ImporterTopLevel(rhinoContext), false);

                // default classes
                addHostObjects(tmpScope, (Class<? extends ScriptableObject>[]) HOSTOBJECT_CLASSES);

                // provided classes
                for (RhinoHostObjectProvider provider : hostObjectProvider) {
                    addHostObjects(tmpScope, provider.getHostObjectClasses());
                    addImportedClasses(rhinoContext, tmpScope, provider.getImportedClasses());
                    addImportedPackages(rhinoContext, tmpScope, provider.getImportedPackages());
                }

                // only assign the root scope when complete set up
                rootScope = tmpScope;

            } finally {
                // ensure the context is exited after setting up the
                // the new root scope
                Context.exit();
            }
        }

        return rootScope;
    }

    private void dropRootScope() {

        // ensure the debugger is closed if the root scope will
        // be replaced to ensure no references to the old scope
        // and context remain
        contextFactory.exitDebugger();

        // drop the scope
        rootScope = null;
    }

    // ---------- SCR integration
    @Activate
    protected void activate(
            final ComponentContext context, final RhinoJavaScriptEngineFactoryConfiguration configuration) {
        Dictionary<?, ?> props = context.getProperties();
        boolean debugging =
                getProperty("org.apache.sling.scripting.javascript.debug", props, context.getBundleContext(), false);

        // try to get the manifest
        String rhinoVersion = null;
        InputStream ins = null;
        try {
            Enumeration<URL> resources =
                    RhinoJavaScriptEngineFactory.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                try {
                    URL url = resources.nextElement();
                    ins = url.openStream();
                    if (ins != null) {
                        Manifest manifest = new Manifest(ins);
                        Attributes attrs = manifest.getMainAttributes();
                        rhinoVersion = attrs.getValue("Rhino-Version");
                        if (rhinoVersion != null) {
                            break;
                        }
                    }
                } finally {
                    if (ins != null) {
                        try {
                            ins.close();
                        } catch (IOException ignore) {
                        }
                    }
                }
            }
        } catch (IOException ioe) {
            log.warn("Unable to read Rhino version.", ioe);
        }

        optimizationLevel = readOptimizationLevel(configuration);

        writeLock.lock();
        try {
            // setup the wrap factory
            wrapFactory = new SlingWrapFactory();

            // initialize the Rhino Context Factory
            contextFactory = SlingContextFactory.getInstance(this, RHINO_LANGUAGE_VERSION);
            contextFactory.setDebugging(debugging);

            setEngineName(getEngineName() + " (Rhino " + (rhinoVersion != null ? rhinoVersion : "unknown") + ")");

            setExtensions(PropertiesUtil.toStringArray(props.get("extensions")));
            setMimeTypes(PropertiesUtil.toStringArray(props.get("mimeTypes")));
            setNames(PropertiesUtil.toStringArray(props.get("names")));

            // set the dynamic class loader as the application class loader
            final DynamicClassLoaderManager dclm = this.dynamicClassLoaderManager;
            if (dclm != null) {
                if (contextFactory.getApplicationClassLoader() == null) {
                    contextFactory.initApplicationClassLoader(dynamicClassLoaderManager.getDynamicClassLoader());
                } else {
                    log.warn("Ignoring multiple dynamic class loader registration attempts.");
                }
            }

            log.info("Activated with optimization level {}", optimizationLevel);
            active = true;
        } catch (RuntimeException e) {
            /**
             * Fail the bundle initialization if SlingContextFactory was not registered as the custom context
             * provide to prevent incorrect scripting processing results.
             */
            log.error(
                    "Cannot register SlingContexrtFactory as custom global context provider.  This could happen if there is another Rhino JavaScript service conflict.");
            throw e;
        } finally {
            writeLock.unlock();
        }
    }

    @Deactivate
    @SuppressWarnings("unused")
    protected void deactivate(ComponentContext context) {
        writeLock.lock();
        try {
            // remove the root scope
            dropRootScope();

            // remove references
            contextFactory = null;
            wrapFactory = null;
            hostObjectProvider.clear();

            active = false;
        } finally {
            writeLock.unlock();
        }
    }

    @SuppressWarnings("unused")
    protected void addHostObjectProvider(RhinoHostObjectProvider provider) {
        hostObjectProvider.add(provider);

        if (rootScope != null) {
            addHostObjects(rootScope, provider.getHostObjectClasses());
        }
    }

    @SuppressWarnings("unused")
    protected void removeHostObjectProvider(RhinoHostObjectProvider provider) {
        // remove the current root scope and have it recreated using the
        // new host object classes
        if (hostObjectProvider.remove(provider)) {
            dropRootScope();
        }
    }

    // ---------- internal

    private void addHostObjects(Scriptable scope, Class<? extends Scriptable>[] classes) {
        if (classes != null) {
            for (Class<? extends Scriptable> clazz : classes) {
                try {

                    // register the host object
                    ScriptableObject.defineClass(scope, clazz);

                    if (SlingWrapper.class.isAssignableFrom(clazz)) {

                        // SlingWrappers can map to several classes if needed
                        final SlingWrapper hostWrapper = (SlingWrapper) clazz.newInstance();
                        for (Class<?> c : hostWrapper.getWrappedClasses()) {
                            getWrapFactory().registerWrapper(c, hostWrapper.getClassName());
                        }
                    } else {
                        // but other Scriptable host objects need to be
                        // registered as well
                        final Scriptable host = clazz.newInstance();
                        getWrapFactory().registerWrapper(host.getClass(), host.getClassName());
                    }
                } catch (Throwable t) {
                    log.warn("addHostObjects: Cannot prepare host object " + clazz, t);
                }
            }
        }
    }

    private void addImportedClasses(Context cx, Scriptable scope, Class<?>[] classes) {
        if (classes != null && classes.length > 0) {
            NativeJavaClass[] np = new NativeJavaClass[classes.length];
            for (int i = 0; i < classes.length; i++) {
                np[i] = new NativeJavaClass(scope, classes[i]);
            }
            ScriptableObject.callMethod(cx, scope, "importClass", np);
        }
    }

    private void addImportedPackages(Context cx, Scriptable scope, String[] packages) {
        if (packages != null && packages.length > 0) {
            NativeJavaPackage[] np = new NativeJavaPackage[packages.length];
            for (int i = 0; i < packages.length; i++) {
                np[i] = new NativeJavaPackage(packages[i]);
            }
            ScriptableObject.callMethod(cx, scope, "importPackage", np);
        }
    }

    private boolean getProperty(
            String name, Dictionary<?, ?> props, BundleContext bundleContext, boolean defaultValue) {
        Object value = props.get(name);
        if (value == null) {
            value = bundleContext.getProperty(name);
        }

        return (value != null) ? Boolean.parseBoolean(String.valueOf(value)) : defaultValue;
    }

    private int readOptimizationLevel(final RhinoJavaScriptEngineFactoryConfiguration configuration) {
        int optLevel = configuration.org_apache_sling_scripting_javascript_rhino_optLevel();
        if (!Context.isValidOptimizationLevel(optLevel)) {
            log.warn("Invalid optimization level {}, using default value", optLevel);
            optLevel = DEFAULT_OPTIMIZATION_LEVEL;
        }
        return optLevel;
    }
}
