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
package org.apache.sling.scripting.javascript.testfixture;

/**
 * Byte-code template for {@code StalePackageCacheRaceConditionTest}. The test reads this
 * class file, renames it to {@code SlingProbeFixtureB} (same length, so the constant pool
 * can be patched in place) and defines it in a custom class loader. The renamed class is
 * therefore loadable <em>only</em> through that loader — never through the test classpath —
 * which is required to simulate a class that only the {@code DynamicClassLoaderManager}
 * can resolve.
 */
public class SlingProbeFixtureA {}
