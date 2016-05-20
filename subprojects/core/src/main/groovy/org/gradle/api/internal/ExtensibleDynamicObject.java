/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal;

import groovy.lang.MissingPropertyException;
import org.gradle.api.internal.plugins.DefaultConvention;
import org.gradle.api.internal.plugins.ExtraPropertiesDynamicObjectAdapter;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.internal.reflect.Instantiator;

import java.util.Map;

/**
 * A {@link DynamicObject} implementation that provides extensibility.
 *
 * This is the dynamic object implementation that “enhanced” objects expose.
 *
 * @see org.gradle.api.internal.AsmBackedClassGenerator.MixInExtensibleDynamicObject
 */
public class ExtensibleDynamicObject extends CompositeDynamicObject implements HasConvention {

    public enum Location {
        BeforeConvention, AfterConvention
    }

    private final AbstractDynamicObject dynamicDelegate;
    private DynamicObject parent;
    private Convention convention;
    private DynamicObject beforeConvention;
    private DynamicObject afterConvention;
    private DynamicObject extraPropertiesDynamicObject;

    /**
     * This variant will internally create a convention that is not fully featured, so should be avoided.
     *
     * Use one of the other variants wherever possible.
     *
     * @param delegate The delegate
     * @see org.gradle.api.internal.plugins.DefaultConvention#DefaultConvention()
     */
    public ExtensibleDynamicObject(Object delegate) {
        this(delegate, new BeanDynamicObject(delegate), new DefaultConvention());
    }

    public ExtensibleDynamicObject(Object delegate, Instantiator instantiator) {
        this(delegate, createDynamicObject(delegate), new DefaultConvention(instantiator));
    }

    public ExtensibleDynamicObject(Object delegate, AbstractDynamicObject dynamicDelegate, Instantiator instantiator) {
        this(delegate, dynamicDelegate, new DefaultConvention(instantiator));
    }

    public ExtensibleDynamicObject(Object delegate, AbstractDynamicObject dynamicDelegate, Convention convention) {
        this.dynamicDelegate = dynamicDelegate;
        this.convention = convention;
        this.extraPropertiesDynamicObject = new ExtraPropertiesDynamicObjectAdapter(delegate.getClass(), convention.getExtraProperties());

        updateDelegates();
    }

    private static BeanDynamicObject createDynamicObject(Object delegate) {
        return new BeanDynamicObject(delegate);
    }

    private void updateDelegates() {
        DynamicObject[] delegates = new DynamicObject[6];
        delegates[0] = dynamicDelegate;
        delegates[1] = extraPropertiesDynamicObject;
        int idx = 2;
        if (beforeConvention != null) {
            delegates[idx++] = beforeConvention;
        }
        if (convention != null) {
            delegates[idx++] = convention.getExtensionsAsDynamicObject();
        }
        if (afterConvention != null) {
            delegates[idx++] = afterConvention;
        }
        boolean addedParent = false;
        if (parent != null) {
            addedParent = true;
            delegates[idx++] = parent;
        }
        DynamicObject[] objects = new DynamicObject[idx];
        System.arraycopy(delegates, 0, objects, 0, idx);
        setObjects(objects);

        if (addedParent) {
            idx--;
        }
        delegates[idx++] = extraPropertiesDynamicObject;
        objects = new DynamicObject[idx];
        System.arraycopy(delegates, 0, objects, 0, idx);
        setObjectsForUpdate(objects);
    }

    protected String getDisplayName() {
        return dynamicDelegate.getDisplayName();
    }

    public ExtraPropertiesExtension getDynamicProperties() {
        return convention.getExtraProperties();
    }

    public void addProperties(Map<String, ?> properties) {
        for (Map.Entry<String, ?> entry : properties.entrySet()) {
            getDynamicProperties().set(entry.getKey(), entry.getValue());
        }
    }

    public DynamicObject getParent() {
        return parent;
    }

    public void setParent(DynamicObject parent) {
        this.parent = parent;
        updateDelegates();
    }

    public Convention getConvention() {
        return convention;
    }

    public void addObject(DynamicObject object, Location location) {
        switch (location) {
            case BeforeConvention:
                beforeConvention = object;
                break;
            case AfterConvention:
                afterConvention = object;
        }
        updateDelegates();
    }

    /**
     * Returns the inheritable properties and methods of this object.
     *
     * @return an object containing the inheritable properties and methods of this object.
     */
    public DynamicObject getInheritable() {
        return new InheritedDynamicObject();
    }

    private ExtensibleDynamicObject snapshotInheritable() {
        AbstractDynamicObject emptyBean = new AbstractDynamicObject() {
            @Override
            protected String getDisplayName() {
                return dynamicDelegate.getDisplayName();
            }
        };

        ExtensibleDynamicObject extensibleDynamicObject = new ExtensibleDynamicObject(emptyBean);

        extensibleDynamicObject.parent = parent;
        extensibleDynamicObject.convention = convention;
        extensibleDynamicObject.extraPropertiesDynamicObject = extraPropertiesDynamicObject;
        if (beforeConvention != null) {
            extensibleDynamicObject.beforeConvention = beforeConvention;
        }
        extensibleDynamicObject.updateDelegates();
        return extensibleDynamicObject;
    }

    private class InheritedDynamicObject implements DynamicObject {
        public void setProperty(String name, Object value) {
            throw new MissingPropertyException(String.format("Could not find property '%s' inherited from %s.", name,
                    dynamicDelegate.getDisplayName()));
        }

        public boolean hasProperty(String name) {
            return snapshotInheritable().hasProperty(name);
        }

        public Object getProperty(String name) {
            return snapshotInheritable().getProperty(name);
        }

        public Map<String, Object> getProperties() {
            return snapshotInheritable().getProperties();
        }

        public boolean hasMethod(String name, Object... arguments) {
            return snapshotInheritable().hasMethod(name, arguments);
        }

        public Object invokeMethod(String name, Object... arguments) {
            return snapshotInheritable().invokeMethod(name, arguments);
        }

        public boolean isMayImplementMissingMethods() {
            return snapshotInheritable().isMayImplementMissingMethods();
        }

        public boolean isMayImplementMissingProperties() {
            return snapshotInheritable().isMayImplementMissingProperties();
        }
    }

}

