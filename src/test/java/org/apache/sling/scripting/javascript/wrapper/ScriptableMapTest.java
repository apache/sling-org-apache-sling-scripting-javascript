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
package org.apache.sling.scripting.javascript.wrapper;

import javax.script.ScriptException;

import java.util.HashMap;

import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.scripting.javascript.RepositoryScriptingTestBase;
import org.apache.sling.scripting.javascript.internal.ScriptEngineHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScriptableMapTest extends RepositoryScriptingTestBase {

    private ValueMap valueMap;
    private ScriptEngineHelper.Data data;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        valueMap = new ValueMapDecorator(new HashMap<String, Object>() {
            {
                put("a", "a");
                put("b", 1);
            }
        });
        data = new ScriptEngineHelper.Data();
        data.put("properties", valueMap);
    }

    @AfterEach
    public void tearDown() throws Exception {
        valueMap.clear();
        data.clear();
        super.tearDown();
    }

    @Test
    void testPropertyAccess() throws ScriptException {
        assertEquals("a", script.eval("properties['a']", data));
        assertEquals("a", script.eval("properties.a", data));
        assertEquals(1, script.eval("properties['b']", data));
        assertEquals(1, script.eval("properties.b", data));
        assertEquals(null, script.eval("properties['c']", data));
    }

    @Test
    void testJavaMethods() throws ScriptException {
        assertEquals(2, script.eval("properties.size()", data));
    }
}
