/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.lang.management.ManagementFactory;

import static org.junit.Assert.assertEquals;

public class TestInfrastructure
{
    // At the moment we run on JDK 11:
    public static final String TARGET_VERSION = "11";

    @Test
    public void testJavaVersion()
    {
        // Check we are running on right JDK.  This matters for e.g. JUnit Quickcheck, as the random number
        // behaviour changes between JDK versions:
        MatcherAssert.assertThat(ManagementFactory.getRuntimeMXBean().getVmVersion(), Matchers.startsWith(TARGET_VERSION));
        assertEquals(TARGET_VERSION, ManagementFactory.getRuntimeMXBean().getSpecVersion());
    }
}
