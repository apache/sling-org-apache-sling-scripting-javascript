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
package org.apache.sling.scripting.javascript.helper;

import java.util.concurrent.atomic.AtomicReference;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.tools.debugger.ScopeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <code>SlingContextFactory</code> extends the standard Rhino
 * ContextFactory to provide customized settings, such as having the dynamic
 * scope feature enabled by default. Other functionality, which may be added
 * would be something like a configurable maximum script runtime value.
 */
public class SlingContextFactory extends ContextFactory {

    /** default log */
    private final Logger log = LoggerFactory.getLogger(getClass());

    private SlingRhinoDebugger debugger;

    private ScopeProvider scopeProvider;

    private boolean debuggerActive;

    private int languageVersion;

    private static final AtomicReference<SlingContextFactory> singleSlingContextFactoryRef = new AtomicReference<>();

    /**
     * @param sp the scope provider
     * @param languageVersion the language version.
     * @return the SlingContextFactory instance that has been successfully registered as the custom global context factory of the Rhino Runtime.
     */
    public static SlingContextFactory getInstance(ScopeProvider sp, int languageVersion) throws RuntimeException {
        if (singleSlingContextFactoryRef.get() == null) {
            synchronized (SlingContextFactory.class) {
                if (singleSlingContextFactoryRef.get() == null) {
                    try {
                        SlingContextFactory factory = new SlingContextFactory(sp, languageVersion);
                        ContextFactory.initGlobal(factory);
                        singleSlingContextFactoryRef.set(factory);
                    } catch (IllegalArgumentException e) {
                        throw new RuntimeException("Fail to set Sling Context Factory as custom context factory", e);
                    } catch (IllegalStateException e) {
                        throw new RuntimeException("Custom context factory already initiated.", e);
                    }
                }
            }
        }
        return singleSlingContextFactoryRef.get();
    }

    private SlingContextFactory(ScopeProvider sp, int languageVersion) {
        scopeProvider = sp;
        this.languageVersion =
                Context.isValidLanguageVersion(languageVersion) ? languageVersion : Context.VERSION_DEFAULT;
    }

    @Override
    protected Context makeContext() {
        Context context = new SlingContext(this);
        context.setLanguageVersion(languageVersion);
        return context;
    }

    @Override
    protected boolean hasFeature(Context cx, int featureIndex) {
        if (featureIndex == Context.FEATURE_DYNAMIC_SCOPE) {
            return true;
        }

        return super.hasFeature(cx, featureIndex);
    }

    @Override
    protected void onContextCreated(Context cx) {
        super.onContextCreated(cx);
        initDebugger(cx);
    }

    private void initDebugger(Context cx) {
        if (isDebugging()) {
            try {
                if (debugger == null) {
                    debugger = new SlingRhinoDebugger(getClass().getSimpleName());
                    debugger.setScopeProvider(scopeProvider);
                    debugger.attachTo(this);
                }
            } catch (Exception e) {
                log.warn("initDebugger: Failed setting up the Rhino debugger", e);
            }
        }
    }

    public void exitDebugger() {
        if (debugger != null) {
            debugger.setScopeProvider(null);
            debugger.detach();
            debugger.dispose();
            debugger = null;
        }
    }

    void debuggerStopped() {
        debugger = null;
    }

    public void setDebugging(boolean enable) {
        debuggerActive = enable;
    }

    public boolean isDebugging() {
        return debuggerActive;
    }
}
