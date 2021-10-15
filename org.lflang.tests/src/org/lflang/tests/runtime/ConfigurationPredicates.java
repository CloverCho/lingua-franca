/*
 * Copyright (c) 2021, TU Dresden.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.lflang.tests.runtime;

import java.util.function.Function;
import java.util.function.Predicate;

import org.lflang.tests.LFTest;
import org.lflang.tests.TestRegistry.TestCategory;

/**
 * Configuration predicates for {@link TestBase} methods.
 *
 * @author Clément Fournier
 */
public class ConfigurationPredicates {

    /**
     * AST transformation that sets the `threads` target property to 0.
     *
     * @return True if successful, false otherwise.
     */
    static boolean makeSingleThreaded(LFTest it) {
        it.getContext().getArgs().setProperty("threads", "0");
        return true;
    }

    static boolean useFourThreads(LFTest t) {
        t.getContext().getArgs().setProperty("threads", "4");
        return true;
    }

    /**
     * Give a handy lambda function that, given a test category,
     * will return true if it is not one of the default excluded
     * categories.
     */
    public static boolean defaultCategoryExclusion(TestCategory category) {
        if (category != TestCategory.CONCURRENT && category != TestCategory.FEDERATED &&
            category != TestCategory.EXAMPLE) {
            // Check if running on Windows
            if (TestBase.isWindows()) {
                // SERIALIZATION and TARGET tests are currently not
                // supported on Windows.
                return category != TestCategory.SERIALIZATION &&
                    category != TestCategory.TARGET;
            }
            return true;
        }
        return false;
    }
}
