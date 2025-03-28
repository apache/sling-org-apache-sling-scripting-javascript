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

import java.util.Arrays;

import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.apache.sling.scripting.api.ScriptCache;
import org.apache.sling.testing.mock.osgi.junit5.OsgiContext;
import org.apache.sling.testing.mock.osgi.junit5.OsgiContextExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OsgiContextExtension.class)
class RhinoJavaScriptEngineFactoryTest {

    private final OsgiContext context = new OsgiContext();

    @Test
    void testRegistrationProperties() {
        DynamicClassLoaderManager dynamicClassLoaderManager = mock(DynamicClassLoaderManager.class);
        when(dynamicClassLoaderManager.getDynamicClassLoader())
                .thenReturn(RhinoJavaScriptEngineFactoryTest.class.getClassLoader());
        context.registerService(DynamicClassLoaderManager.class, dynamicClassLoaderManager);
        context.registerService(ScriptCache.class, mock(ScriptCache.class));
        context.registerInjectActivateService(new RhinoJavaScriptEngineFactory());
        RhinoJavaScriptEngineFactory instance =
                (RhinoJavaScriptEngineFactory) context.getService(ScriptEngineFactory.class);
        assertEquals(
                Arrays.asList("rhino", "Rhino", "javascript", "JavaScript", "ecmascript", "ECMAScript"),
                instance.getNames());
        assertEquals("ECMAScript", instance.getLanguageName());
        assertEquals("partial ECMAScript 2015 support", instance.getLanguageVersion());
        assertTrue(
                instance.getEngineName() != null && instance.getEngineName().contains("Rhino 1.7.7.1_1"),
                "Unexpected engine name");
    }
}
