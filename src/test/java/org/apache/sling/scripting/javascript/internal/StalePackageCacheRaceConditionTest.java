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

import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.apache.sling.scripting.api.ScriptCache;
import org.apache.sling.testing.mock.osgi.junit5.OsgiContext;
import org.apache.sling.testing.mock.osgi.junit5.OsgiContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mozilla.javascript.Context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for SLING-13207: the shared {@code rootScope} of
 * {@code RhinoJavaScriptEngineFactory} permanently caches failed class lookups as
 * {@code NativeJavaPackage} objects. A class lookup that fails while the
 * {@code DynamicClassLoaderManager}'s {@code ClassLoaderFacade} is stale (the window during
 * any bundle restart) poisons the scope for every subsequent {@link javax.script.ScriptEngine},
 * even after the class becomes loadable again.
 *
 * <p>The fix: when the {@code DynamicClassLoaderManager} is re-registered (triggered by
 * {@code DynamicClassLoaderManagerFactory.Activator.bundleChanged()} on any bundle restart),
 * {@code bindDynamicClassLoaderManager()} drops the {@code rootScope} so the stale
 * {@code NativeJavaPackage} tree is discarded and all lookups are retried with the fresh
 * class loader.
 *
 * <p>The probe class used by these tests exists <em>only</em> in the test's
 * {@link SwitchableClassLoader} (defined at runtime from renamed byte code), never on the
 * test classpath. This matters because the factory's application class loader falls back to
 * the bundle class loader when the dynamic class loader fails — a class visible on the test
 * classpath could never poison the scope.
 */
@ExtendWith(OsgiContextExtension.class)
class StalePackageCacheRaceConditionTest {

    /**
     * Binary name of the runtime-defined probe class. Must not exist on the test classpath;
     * it is derived from {@code SlingProbeFixtureA} by an equal-length rename.
     */
    private static final String DYNAMIC_ONLY_CLASS =
            "org.apache.sling.scripting.javascript.testfixture.SlingProbeFixtureB";

    private static final String TEMPLATE_CLASS_RESOURCE =
            "/org/apache/sling/scripting/javascript/testfixture/SlingProbeFixtureA.class";

    /**
     * {@code typeof} distinguishes a resolved class ({@code NativeJavaClass} → "function")
     * from a cached failed lookup ({@code NativeJavaPackage} → "object").
     */
    private static final String PROBE = "typeof Packages." + DYNAMIC_ONLY_CLASS;

    /**
     * Class loader that is the only source of {@link #DYNAMIC_ONLY_CLASS}. While blocked it
     * throws {@code ClassNotFoundException} for that class, simulating the stale
     * {@code ClassLoaderFacade} during a bundle restart. All other names are delegated to the
     * parent.
     */
    static class SwitchableClassLoader extends ClassLoader {
        private volatile boolean blocked;
        private Class<?> probeClass;

        SwitchableClassLoader(ClassLoader parent) {
            super(parent);
        }

        void block() {
            blocked = true;
        }

        void unblock() {
            blocked = false;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (DYNAMIC_ONLY_CLASS.equals(name)) {
                if (blocked) {
                    throw new ClassNotFoundException(
                            "Simulating stale ClassLoaderFacade during bundle restart: " + name);
                }
                synchronized (this) {
                    if (probeClass == null) {
                        byte[] bytes = probeClassBytes();
                        probeClass = defineClass(name, bytes, 0, bytes.length);
                    }
                }
                return probeClass;
            }
            return super.loadClass(name);
        }
    }

    /** Byte code of {@code SlingProbeFixtureA}, renamed in place to {@code SlingProbeFixtureB}. */
    private static byte[] probeClassBytes() {
        byte[] data;
        try (InputStream in = StalePackageCacheRaceConditionTest.class.getResourceAsStream(TEMPLATE_CLASS_RESOURCE)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            data = buffer.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read template class " + TEMPLATE_CLASS_RESOURCE, e);
        }
        byte[] from = "SlingProbeFixtureA".getBytes(StandardCharsets.US_ASCII);
        byte[] to = "SlingProbeFixtureB".getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i <= data.length - from.length; i++) {
            boolean match = true;
            for (int j = 0; j < from.length; j++) {
                if (data[i + j] != from[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                System.arraycopy(to, 0, data, i, to.length);
            }
        }
        return data;
    }

    private final OsgiContext context = new OsgiContext();

    private SwitchableClassLoader switchableLoader;
    private RhinoJavaScriptEngineFactory rhinoFactory;
    private ScriptEngineFactory factory;

    @BeforeEach
    void setUp() {
        assertNull(
                Context.getCurrentContext(),
                "Another test leaked an entered Rhino Context on this thread; it would pin a stale "
                        + "ContextFactory/application class loader and invalidate these tests");

        switchableLoader = new SwitchableClassLoader(StalePackageCacheRaceConditionTest.class.getClassLoader());

        DynamicClassLoaderManager dclm = mock(DynamicClassLoaderManager.class);
        when(dclm.getDynamicClassLoader()).thenReturn(switchableLoader);
        context.registerService(DynamicClassLoaderManager.class, dclm);
        context.registerService(ScriptCache.class, mock(ScriptCache.class));

        rhinoFactory = new RhinoJavaScriptEngineFactory();
        context.registerInjectActivateService(rhinoFactory);
        factory = context.getService(ScriptEngineFactory.class);
    }

    private void assertProbe(String expectedType, String message) throws ScriptException {
        assertEquals(expectedType, factory.getScriptEngine().eval(PROBE), message);
    }

    /** Re-registers a fresh {@code DynamicClassLoaderManager} — the production rebind code path. */
    private void rebindDynamicClassLoaderManager() {
        DynamicClassLoaderManager freshDclm = mock(DynamicClassLoaderManager.class);
        when(freshDclm.getDynamicClassLoader()).thenReturn(switchableLoader);
        rhinoFactory.bindDynamicClassLoaderManager(freshDclm);
    }

    /** Under normal conditions the probe class resolves to a {@code NativeJavaClass}. */
    @Test
    void classResolution_returnsNativeJavaClass_underNormalConditions() throws ScriptException {
        assertProbe("function", "Probe class must resolve to NativeJavaClass (typeof === 'function')");
    }

    /**
     * Reproduces SLING-13207 end to end: a lookup failure during the stale-classloader window
     * poisons the shared {@code rootScope} and — this is the bug — the poisoning survives the
     * class becoming loadable again. Only the {@code DynamicClassLoaderManager} rebind, which
     * drops the {@code rootScope}, recovers.
     */
    @Test
    void classResolution_recoversAfterClassloaderFailureAndRebind() throws ScriptException {
        // Stale-classloader window during a bundle restart: the lookup fails and the failure
        // is cached as a NativeJavaPackage in the shared rootScope.
        switchableLoader.block();
        assertProbe("object", "Failed lookup must be cached as NativeJavaPackage (typeof === 'object')");

        // The class is loadable again, but the poisoned cache persists — this is SLING-13207.
        switchableLoader.unblock();
        assertProbe("object", "Without a rebind the stale NativeJavaPackage cache must persist (the bug)");

        // Bundle restart completes: DynamicClassLoaderManager is re-registered, the bind
        // method drops the rootScope and the lookup is retried from scratch.
        rebindDynamicClassLoaderManager();
        assertProbe("function", "After the DynamicClassLoaderManager rebind the probe class must resolve");
    }

    /**
     * After a rebind, all engine instances — not just the first one — resolve the probe class
     * correctly: the shared {@code rootScope} is fully rebuilt, not partially stale.
     */
    @Test
    void classResolution_allEnginesHealthyAfterRebind() throws ScriptException {
        switchableLoader.block();
        assertProbe("object", "Failed lookup must poison the shared rootScope");

        switchableLoader.unblock();
        rebindDynamicClassLoaderManager();

        for (int i = 0; i < 5; i++) {
            assertProbe("function", "Engine #" + i + " after rebind must resolve the probe class");
        }
    }
}
