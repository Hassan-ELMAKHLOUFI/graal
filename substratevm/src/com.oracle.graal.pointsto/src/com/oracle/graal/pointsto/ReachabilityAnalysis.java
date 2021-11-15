/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.pointsto;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.typestate.TypeState;
import jdk.vm.ci.code.BytecodePosition;

import java.lang.reflect.Executable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Interface to be used to query and change the state of the static analysis in Native Image.
 *
 * It is a first small step in a bigger effort to group together all requests changing the analysis
 * state.
 */
public interface ReachabilityAnalysis {

    /**
     * Marks given class and all its superclasses as reachable.
     * 
     * @param clazz class to be marked
     * @param addFields if true, all instance fiels are marked as accessed
     * @param addArrayClass if true, the array class is registered as well
     */
    AnalysisType addRootClass(Class<?> clazz, boolean addFields, boolean addArrayClass);

    /**
     * Marks given field as accessed.
     */
    AnalysisType addRootField(Class<?> clazz, String fieldName);

    /**
     * Marks given method as reachable.
     */
    AnalysisMethod addRootMethod(AnalysisMethod aMethod);

    /**
     * Marks given method as reachable.
     */
    AnalysisMethod addRootMethod(Executable method);

    /**
     * Marks given method as reachable.
     */
    AnalysisMethod addRootMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes);

    /**
     * Waits until the analysis is done.
     */
    boolean finish() throws InterruptedException;

    /**
     * Clears all intermediary data to reduce the footprint.
     */
    void cleanupAfterAnalysis();

    /**
     * Force update of the unsafe loads and unsafe store type flows when a field is registered as
     * unsafe accessed 'on the fly', i.e., during the analysis.
     *
     * @param field the newly unsafe registered field. We use its declaring type to filter the
     *            unsafe access flows that need to be updated.
     */
    void forceUnsafeUpdate(AnalysisField field);

    /**
     * Performs any necessary additional steps required by the analysis to handle JNI accessed
     * fields.
     */
    void registerAsJNIAccessed(AnalysisField field, boolean writable);

    /**
     * @return typestate representing all types that need synchronization
     */
    TypeState getAllSynchronizedTypeState();

    /**
     * @return query interface used for looking up analysis objects from java.lang.reflect
     *         references
     */
    AnalysisMetaAccess getMetaAccess();

    /**
     * @return universe containing all known analysis types
     */
    AnalysisUniverse getUniverse();

    /**
     * @return policy used when running the analysis
     */
    AnalysisPolicy analysisPolicy();

    List<InvokeInfo> getInvokes(AnalysisMethod method);

    class InvokeInfo {
        private final AnalysisMethod targetMethod;
        private final Collection<AnalysisMethod> possibleCallees;
        private final BytecodePosition position;
        private final boolean isDirect;

        public static InvokeInfo direct(AnalysisMethod targetMethod, BytecodePosition position) {
            return new InvokeInfo(targetMethod, Collections.singletonList(targetMethod), position, true);
        }

        public static InvokeInfo virtual(AnalysisMethod targetMethod, Collection<AnalysisMethod> possibleCallees, BytecodePosition position) {
            return new InvokeInfo(targetMethod, possibleCallees, position, false);
        }

        private InvokeInfo(AnalysisMethod targetMethod, Collection<AnalysisMethod> possibleCallees, BytecodePosition position, boolean isDirect) {
            this.targetMethod = targetMethod;
            this.possibleCallees = possibleCallees;
            this.position = position;
            this.isDirect = isDirect;
        }

        public boolean canBeStaticallyBound() {
            return targetMethod.canBeStaticallyBound() || possibleCallees.size() == 1;
        }

        public AnalysisMethod getTargetMethod() {
            return targetMethod;
        }

        public Collection<AnalysisMethod> getPossibleCallees() {
            return possibleCallees;
        }

        public BytecodePosition getPosition() {
            return position;
        }

        public boolean isDirect() {
            return isDirect;
        }
    }
}
