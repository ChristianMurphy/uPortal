/**
 * Licensed to Apereo under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright ownership. Apereo
 * licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at the
 * following location:
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apereo.portal.dbmigrate.testsupport;

import java.io.PrintStream;

/**
 * A {@link PrintStream} that drops text printed from inside one named method and passes everything
 * else through. It decides by the caller on the stack, so it never needs the dropped text itself.
 */
final class OutputFromMethodFilter extends PrintStream {

    private static final StackWalker WALKER = StackWalker.getInstance();

    private final String className;
    private final String methodName;

    OutputFromMethodFilter(PrintStream target, String className, String methodName) {
        super(target, true);
        this.className = className;
        this.methodName = methodName;
    }

    String className() {
        return className;
    }

    String methodName() {
        return methodName;
    }

    @Override
    public void print(String text) {
        if (!calledFromFilteredMethod()) {
            super.print(text);
        }
    }

    @Override
    public void println(String text) {
        if (!calledFromFilteredMethod()) {
            super.println(text);
        }
    }

    private boolean calledFromFilteredMethod() {
        return WALKER.walk(
                frames ->
                        frames.limit(16)
                                .anyMatch(
                                        frame ->
                                                frame.getClassName().equals(className)
                                                        && frame.getMethodName()
                                                                .equals(methodName)));
    }
}
