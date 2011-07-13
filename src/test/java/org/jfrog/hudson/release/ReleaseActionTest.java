/*
 * Copyright (C) 2011 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jfrog.hudson.release;

import org.jfrog.hudson.release.maven.MavenReleaseAction;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests the {@link ReleaseAction}.
 *
 * @author Yossi Shaul
 */
public class ReleaseActionTest {

    private ReleaseAction action;

    @Before
    public void setup() {
        action = new MavenReleaseAction(null);
    }


    @Test
    public void nextVersionSimpleMinor() {
        assertEquals("1.3-SNAPSHOT", action.calculateNextVersion("1.2"));
        assertEquals("3.2.13-SNAPSHOT", action.calculateNextVersion("3.2.12"));
    }

    @Test
    public void nextVersionNoMinor() {
        assertEquals("2-SNAPSHOT", action.calculateNextVersion("1"));
        assertEquals("939-SNAPSHOT", action.calculateNextVersion("938"));
    }

    @Test
    public void nextVersionCompoundMinor() {
        assertEquals("1.2.3-5-SNAPSHOT", action.calculateNextVersion("1.2.3-4"));
    }

    @Test
    public void unsupportedVersions() {
        // currently unsupported until custom/improved logic implementation
        assertEquals("1-beta", action.calculateNextVersion("1-beta"));
        assertEquals("1.2-alpha", action.calculateNextVersion("1.2-alpha"));
    }
}
