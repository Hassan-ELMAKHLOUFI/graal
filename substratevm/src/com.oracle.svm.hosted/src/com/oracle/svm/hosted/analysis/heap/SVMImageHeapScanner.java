/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.analysis.heap;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;

import com.oracle.graal.pointsto.ObjectScanningObserver;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.heap.ImageHeap;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.graal.pointsto.heap.value.ValueSupplier;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.svm.core.meta.ReadableJavaField;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.ameta.AnalysisConstantReflectionProvider;
import com.oracle.svm.hosted.analysis.DynamicHubInitializer;
import com.oracle.svm.hosted.meta.HostedMetaAccess;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;

public class SVMImageHeapScanner extends ImageHeapScanner {

    private final DynamicHubInitializer dynamicHubInitializer;
    protected HostedMetaAccess hostedMetaAccess;

    public SVMImageHeapScanner(ImageHeap imageHeap, AnalysisUniverse universe, AnalysisMetaAccess metaAccess,
                    SnippetReflectionProvider snippetReflection, ConstantReflectionProvider constantReflection, ObjectScanningObserver aScanningObserver,
                    UnsupportedFeatures unsupportedFeatures) {
        super(imageHeap, universe, metaAccess, snippetReflection, constantReflection, aScanningObserver);
        dynamicHubInitializer = new DynamicHubInitializer(universe, metaAccess, unsupportedFeatures, constantReflection);
    }

    public void setHostedMetaAccess(HostedMetaAccess hostedMetaAccess) {
        this.hostedMetaAccess = hostedMetaAccess;
    }

    @Override
    protected boolean initializeAtRunTime(AnalysisType type) {
        return ((SVMHost) hostVM).getClassInitializationSupport().shouldInitializeAtRuntime(type);
    }

    @Override
    protected AnalysisFuture<ImageHeap.ImageHeapObject> getOrCreateConstantReachableTask(JavaConstant javaConstant, Reason reason) {
        VMError.guarantee(javaConstant instanceof SubstrateObjectConstant, "Not a substrate constant " + javaConstant);
        return super.getOrCreateConstantReachableTask(javaConstant, reason);
    }

    @Override
    protected Object unwrapObject(JavaConstant constant) {
        /*
         * Unwrap the original object from the constant. Unlike HostedSnippetReflectionProvider this
         * will just return the wrapped object, without any transformation. This is important during
         * scanning: when scanning a java.lang.Class it will be replaced by a DynamicHub which is
         * then actually scanned. The HostedSnippetReflectionProvider returns the original Class for
         * a DynamicHub, which would lead to a deadlock during scanning.
         */
        return SubstrateObjectConstant.asObject(Object.class, constant);
    }

    @Override
    public boolean isValueAvailable(AnalysisField field) {
        if (field.wrapped instanceof ReadableJavaField) {
            ReadableJavaField readableField = (ReadableJavaField) field.wrapped;
            return readableField.isValueAvailable();
        }
        return super.isValueAvailable(field);
    }

    @Override
    protected ValueSupplier<JavaConstant> readHostedFieldValue(AnalysisField field, JavaConstant object) {
        if (field.isStatic() && initializeAtRunTime(field.getDeclaringClass())) {
            return ValueSupplier.eagerValue(AnalysisConstantReflectionProvider.readUninitializedStaticValue(field));
        }

        if (field.wrapped instanceof ReadableJavaField) {
            ReadableJavaField readableField = (ReadableJavaField) field.wrapped;
            if (readableField.isValueValidForAnalysis()) {
                /* Materialize and return the value. */
                JavaConstant value = universe.lookup(readableField.readValue(metaAccess, object));
                return ValueSupplier.eagerValue(value);
            } else {
                /*
                 * Return a lazy value. This applies to RecomputeFieldValue.Kind.FieldOffset and
                 * RecomputeFieldValue.Kind.Custom. The value becomes available during hosted
                 * universe building and is installed by calling
                 * ComputedValueField.processSubstrate() or by ComputedValueField.readValue().
                 * Attempts to materialize the value earlier will result in an error.
                 */
                return ValueSupplier.lazyValue(() -> universe.lookup(readableField.readValue(hostedMetaAccess, object)),
                                readableField::isValueAvailable);
            }
        }
        return super.readHostedFieldValue(field, object);
    }

    @Override
    protected JavaConstant transformFieldValue(AnalysisField field, JavaConstant receiverConstant, JavaConstant originalValueConstant) {
        /*
         * Intercept value without running object replacements which is already performed when the
         * raw value is shadowed.
         */
        return AnalysisConstantReflectionProvider.interceptValue(universe, field, originalValueConstant);
    }

    @Override
    public void scanHub(AnalysisType type) {
        /* Initialize dynamic hub metadata before scanning it. */
        dynamicHubInitializer.initializeMetaData(this, type);
        super.scanHub(type);
        AnalysisType enclosingType = type.getEnclosingType();
        if (enclosingType != null) {
            super.scanHub(enclosingType);
        }
    }
}
