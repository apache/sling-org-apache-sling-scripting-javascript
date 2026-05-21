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

import java.util.Arrays;

import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.apache.sling.scripting.api.ScriptCache;
import org.apache.sling.scripting.javascript.RhinoHostObjectProvider;
import org.apache.sling.testing.mock.osgi.junit5.OsgiContext;
import org.apache.sling.testing.mock.osgi.junit5.OsgiContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OsgiContextExtension.class)
class RhinoJavaScriptEngineFactoryTest {

    private final OsgiContext context = new OsgiContext();

    private RhinoJavaScriptEngineFactory instance;

    @BeforeEach
    void setUp() {
        DynamicClassLoaderManager dynamicClassLoaderManager = mock(DynamicClassLoaderManager.class);
        when(dynamicClassLoaderManager.getDynamicClassLoader())
                .thenReturn(RhinoJavaScriptEngineFactoryTest.class.getClassLoader());
        context.registerService(DynamicClassLoaderManager.class, dynamicClassLoaderManager);
        context.registerService(ScriptCache.class, mock(ScriptCache.class));
        context.registerInjectActivateService(new RhinoJavaScriptEngineFactory());
        instance = (RhinoJavaScriptEngineFactory) context.getService(ScriptEngineFactory.class);
    }

    @Test
    void testRegistrationProperties() {
        assertEquals(
                Arrays.asList("rhino", "Rhino", "javascript", "JavaScript", "ecmascript", "ECMAScript"),
                instance.getNames());
        assertEquals("ECMAScript", instance.getLanguageName());
        assertEquals("partial ECMAScript 2015 support", instance.getLanguageVersion());
        assertTrue(
                instance.getEngineName() != null && instance.getEngineName().contains("Rhino 1.7.7.1_1"),
                "Unexpected engine name");
    }

    @Test
    void testGetScriptEngineReturnsEngineWhenActive() {
        ScriptEngine engine = instance.getScriptEngine();
        assertNotNull(engine, "Expected a non-null ScriptEngine when factory is active");
    }

    @Test
    void testGetScopeReturnsRootScopeWhenActive() {
        Scriptable scope = instance.getScope();
        assertNotNull(scope, "Expected a non-null scope when factory is active");
    }

    @Test
    void testGetParameterThreading() {
        assertEquals("MULTITHREADED", instance.getParameter("THREADING"));
    }

    @Test
    void testGetParameterOther() {
        // Delegates to super – ENGINE_VERSION is populated by AbstractScriptEngineFactory
        assertNotNull(instance.getParameter(ScriptEngine.ENGINE_VERSION));
    }

    @Test
    void testGetOptimizationLevel() {
        assertEquals(RhinoJavaScriptEngineFactory.DEFAULT_OPTIMIZATION_LEVEL, instance.getOptimizationLevel());
    }

    @Test
    void testRhinoLanguageVersion() {
        assertEquals(Context.VERSION_ES6, instance.rhinoLanguageVersion());
    }

    @Test
    void testInvalidOptimizationLevelFallsBackToDefault() {
        // Re-activate the existing instance with an invalid optLevel via the same OsgiContext.
        // Deactivate first so the Rhino global ContextFactory is torn down cleanly.
        instance.deactivate(context.componentContext());
        context.registerInjectActivateService(
                instance, "org.apache.sling.scripting.javascript.rhino.optLevel", 99 // invalid level
                );
        assertEquals(
                RhinoJavaScriptEngineFactory.DEFAULT_OPTIMIZATION_LEVEL,
                instance.getOptimizationLevel(),
                "Invalid optLevel should fall back to default");
    }

    @Test
    void testBindAndUnbindDynamicClassLoaderManager() {
        Scriptable scopeBeforeRebind = instance.getScope();
        assertNotNull(scopeBeforeRebind);

        // Rebind with a new DCLM – must drop the root scope so it is rebuilt (SLING-13207)
        DynamicClassLoaderManager newDclm = mock(DynamicClassLoaderManager.class);
        when(newDclm.getDynamicClassLoader()).thenReturn(RhinoJavaScriptEngineFactoryTest.class.getClassLoader());
        instance.bindDynamicClassLoaderManager(newDclm);
        Scriptable scopeAfterRebind = instance.getScope();
        assertNotNull(scopeAfterRebind);
        assertNotSame(
                scopeBeforeRebind,
                scopeAfterRebind,
                "Rebinding the DynamicClassLoaderManager must drop and rebuild the root scope");

        // Unbind the new DCLM
        instance.unbindDynamicClassLoaderManager(newDclm);
        // getScope() should still return non-null (falls back to parent classloader)
        assertNotNull(instance.getScope());
    }

    @Test
    void testUnbindWithWrongDclmIsNoop() {
        // Unbinding a different instance should not null out the current one
        DynamicClassLoaderManager otherDclm = mock(DynamicClassLoaderManager.class);
        instance.unbindDynamicClassLoaderManager(otherDclm);
        // factory should still be usable
        assertNotNull(instance.getScriptEngine());
    }

    @Test
    void testAddAndRemoveHostObjectProvider() {
        RhinoHostObjectProvider provider = mock(RhinoHostObjectProvider.class);
        when(provider.getHostObjectClasses()).thenReturn(new Class[0]);
        when(provider.getImportedClasses()).thenReturn(new Class[0]);
        when(provider.getImportedPackages()).thenReturn(new String[0]);

        // Adding a provider should not throw
        instance.addHostObjectProvider(provider);

        // Removing the provider should drop the root scope (no exception expected)
        instance.removeHostObjectProvider(provider);

        // Factory should still be functional after scope drop
        assertNotNull(instance.getScriptEngine());
    }

    @Test
    void testDeactivateAndGetScriptEngineReturnsNull() {
        instance.deactivate(context.componentContext());
        assertNull(instance.getScriptEngine(), "Expected null ScriptEngine after deactivation");
    }

    @Test
    void testDeactivateAndGetScopeReturnsNull() {
        instance.deactivate(context.componentContext());
        assertNull(instance.getScope(), "Expected null scope after deactivation");
    }
}
