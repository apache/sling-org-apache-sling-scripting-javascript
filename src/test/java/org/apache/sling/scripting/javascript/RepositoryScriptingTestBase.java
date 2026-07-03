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
package org.apache.sling.scripting.javascript;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.naming.NamingException;

import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.apache.sling.commons.testing.jcr.RepositoryTestBase;
import org.apache.sling.scripting.api.ScriptCache;
import org.apache.sling.scripting.javascript.internal.RhinoJavaScriptEngineFactory;
import org.apache.sling.scripting.javascript.internal.ScriptEngineHelper;
import org.apache.sling.testing.mock.osgi.junit5.OsgiContext;
import org.apache.sling.testing.mock.osgi.junit5.OsgiContextExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Base class for tests which need a Repository and scripting functionality */
@ExtendWith(OsgiContextExtension.class)
public class RepositoryScriptingTestBase extends RepositoryTestBase {

    private final OsgiContext osgiContext = new OsgiContext();
    protected ScriptEngineHelper script;
    private int counter;

    @Override
    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();
        DynamicClassLoaderManager dclm = mock(DynamicClassLoaderManager.class);
        when(dclm.getDynamicClassLoader()).thenReturn(getClass().getClassLoader());
        osgiContext.registerService(DynamicClassLoaderManager.class, dclm);
        osgiContext.registerService(ScriptCache.class, mock(ScriptCache.class));
        RhinoJavaScriptEngineFactory factory = new RhinoJavaScriptEngineFactory();
        osgiContext.registerInjectActivateService(factory);
        script = new ScriptEngineHelper(factory.getScriptEngine());
    }

    @Override
    @AfterEach
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    protected Node getNewNode() throws RepositoryException, NamingException {
        return getTestRootNode().addNode("test-" + (++counter));
    }
}
