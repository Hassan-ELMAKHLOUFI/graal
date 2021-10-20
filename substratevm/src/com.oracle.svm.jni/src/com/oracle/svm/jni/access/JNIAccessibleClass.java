/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni.access;

import java.util.Collections;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.hosted.FeatureImpl;

import jdk.vm.ci.meta.MetaUtil;

/**
 * Information on a class that can be looked up and accessed via JNI.
 */
public final class JNIAccessibleClass {
    private final Class<?> classObject;
    @UnknownObjectField(fullyQualifiedTypes = "org.graalvm.collections.EconomicMapImpl") //
    private EconomicMap<JNIAccessibleMethodDescriptor, JNIAccessibleMethod> methods;
    @UnknownObjectField(fullyQualifiedTypes = "org.graalvm.collections.EconomicMapImpl") //
    private EconomicMap<String, JNIAccessibleField> fields;

    JNIAccessibleClass(Class<?> clazz) {
        this.classObject = clazz;
    }

    public Class<?> getClassObject() {
        return classObject;
    }

    public Iterable<JNIAccessibleField> getFields() {
        return (fields != null) ? fields.getValues() : Collections::emptyIterator;
    }

    MapCursor<String, JNIAccessibleField> getFieldsByName() {
        return (fields != null) ? fields.getEntries() : EconomicMap.emptyCursor();
    }

    public JNIAccessibleField getField(String name) {
        return (fields != null) ? fields.get(name) : null;
    }

    @Platforms(HOSTED_ONLY.class)
    void addFieldIfAbsent(FeatureImpl.DuringAnalysisAccessImpl access, String name, Function<String, JNIAccessibleField> mappingFunction) {
        if (fields == null) {
            fields = ImageHeapMap.create();
            access.rescanObject(fields);
        }
        if (!fields.containsKey(name)) {
            fields.put(name, mappingFunction.apply(name));
            access.rescanObject(fields);
        }
    }

    @Platforms(HOSTED_ONLY.class)
    void addMethodIfAbsent(FeatureImpl.DuringAnalysisAccessImpl access, JNIAccessibleMethodDescriptor descriptor, Function<JNIAccessibleMethodDescriptor, JNIAccessibleMethod> mappingFunction) {
        if (methods == null) {
            methods = ImageHeapMap.create();
            access.rescanObject(methods);
        }
        if (!methods.containsKey(descriptor)) {
            methods.put(descriptor, mappingFunction.apply(descriptor));
            access.rescanObject(methods);
        }
    }

    public Iterable<JNIAccessibleMethod> getMethods() {
        return (methods != null) ? methods.getValues() : Collections::emptyIterator;
    }

    public MapCursor<JNIAccessibleMethodDescriptor, JNIAccessibleMethod> getMethodsByDescriptor() {
        return (methods != null) ? methods.getEntries() : EconomicMap.emptyCursor();
    }

    public JNIAccessibleMethod getMethod(JNIAccessibleMethodDescriptor descriptor) {
        return (methods != null) ? methods.get(descriptor) : null;
    }

    String getInternalName() {
        return MetaUtil.toInternalName(classObject.getName());
    }
}
