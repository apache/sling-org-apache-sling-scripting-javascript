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
import javax.jcr.Session;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.apache.sling.scripting.api.ScriptCache;
import org.apache.sling.scripting.javascript.internal.RhinoJavaScriptEngineFactory;
import org.apache.sling.scripting.javascript.internal.ScriptEngineHelper;
import org.apache.sling.testing.mock.osgi.MockOsgi;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Base class for tests which need a Repository and scripting functionality.
 */
@ExtendWith(SlingContextExtension.class)
public class RepositoryScriptingTestBase {

    /** Unique suffix for the per-test root node; the Oak repository is shared across the JVM. */
    private static final AtomicInteger rootCounter = new AtomicInteger();

    protected final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);
    protected ScriptEngineHelper script;
    protected Session session;
    private RhinoJavaScriptEngineFactory factory;
    private Node testRootNode;
    private int counter;

    @BeforeEach
    protected void setUp() throws Exception {
        DynamicClassLoaderManager dclm = mock(DynamicClassLoaderManager.class);
        when(dclm.getDynamicClassLoader()).thenReturn(getClass().getClassLoader());
        context.registerService(DynamicClassLoaderManager.class, dclm);
        context.registerService(ScriptCache.class, mock(ScriptCache.class));
        factory = new RhinoJavaScriptEngineFactory();
        context.registerInjectActivateService(factory);
        script = new ScriptEngineHelper(factory.getScriptEngine());
        session = context.resourceResolver().adaptTo(Session.class);
    }

    @AfterEach
    protected void tearDown() throws Exception {
        // Rhino's global ContextFactory keeps a set-once application class loader; deactivating the
        // factory disposes it so the next activation in this JVM can set it again cleanly.
        if (factory != null) {
            MockOsgi.deactivate(factory, context.bundleContext());
            factory = null;
        }
        if (session != null && session.isLive()) {
            session.logout();
        }
    }

    protected Session getSession() {
        return session;
    }

    protected Node getTestRootNode() throws RepositoryException {
        if (testRootNode == null) {
            testRootNode = session.getRootNode().addNode("test_" + rootCounter.incrementAndGet(), "nt:unstructured");
            session.save();
        }
        return testRootNode;
    }

    protected Node getNewNode() throws RepositoryException {
        return getTestRootNode().addNode("test-" + (++counter));
    }
}
