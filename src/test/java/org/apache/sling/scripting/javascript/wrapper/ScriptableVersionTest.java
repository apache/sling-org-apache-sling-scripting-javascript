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

import javax.jcr.Node;

import org.apache.sling.scripting.javascript.RepositoryScriptingTestBase;
import org.apache.sling.scripting.javascript.internal.ScriptEngineHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Test access to Version and VersionHistory objects */
class ScriptableVersionTest extends RepositoryScriptingTestBase {

    private Node node;
    private ScriptEngineHelper.Data data = new ScriptEngineHelper.Data();

    @Override
    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();

        node = getNewNode();
        data.put("node", node);
        node.addMixin("mix:versionable");
        getSession().save();

        node.setProperty("Modified", "Just making sure we have a second version");
        getSession().save();
    }

    @Test
    void testVersionHistoryAccess() throws Exception {
        Object result = script.eval("node.getVersionHistory().getAllVersions()", data);
        assertNotNull(result);
    }

    @Test
    void testVersionHistoryIsWrapped() throws Exception {
        assertEquals("nt:versionHistory", script.eval("node.versionHistory['jcr:primaryType']", data));
        assertEquals("nt:version", script.eval("node.versionHistory.rootVersion['jcr:primaryType']", data));
    }

    @Test
    void testVersionAccess() throws Exception {
        Object result = script.eval("node.getBaseVersion().getCreated()", data);
        assertNotNull(result);
    }

    @Test
    void testVersionIsWrapped() throws Exception {
        assertEquals("nt:version", script.eval("node.baseVersion['jcr:primaryType']", data));
        assertNotNull(script.eval("node.baseVersion.created", data));
    }
}
