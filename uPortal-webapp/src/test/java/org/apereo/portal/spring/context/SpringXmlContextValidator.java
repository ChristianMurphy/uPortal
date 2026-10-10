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
package org.apereo.portal.spring.context;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.HierarchicalBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.PlaceholderConfigurerSupport;
import org.springframework.beans.factory.config.RuntimeBeanNameReference;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.LookupOverride;
import org.springframework.beans.factory.support.MethodOverride;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericXmlApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Loads Spring XML application contexts as far as is possible without a database, JNDI or a servlet
 * container, and checks every bean definition in them.
 *
 * <p>Loading runs the first half of {@link GenericXmlApplicationContext#refresh()}: the XML is
 * parsed with XSD validation (so an element or attribute that the schema no longer declares fails),
 * the namespace handlers ({@code context:}, {@code security:}, {@code mvc:}, {@code
 * webflow-config:} and so on) register their beans, {@code @Configuration} classes found by
 * component scanning are expanded, and the bean factory post-processors run (so property
 * placeholders are resolved). No singleton is created.
 *
 * <p>{@link #validate} then checks what a full refresh would trip over when it wires the beans:
 * every bean class and factory method exists, every {@code <property>} has a setter, every
 * constructor argument list matches a constructor, every bean reference names a bean, and every
 * init, destroy and lookup method exists. Inner beans and beans inside lists, sets and maps are
 * checked too.
 */
public final class SpringXmlContextValidator {

    /** A context that stops {@link #refresh()} after the bean factory post-processors. */
    public static final class DefinitionsOnlyContext extends GenericXmlApplicationContext {
        public DefinitionsOnlyContext(ApplicationContext parent, Resource... resources) {
            setParent(parent);
            setValidating(true);
            load(resources);
        }

        /**
         * Parse and register the bean definitions, then run the post-processors that change
         * definitions: those that register beans ({@code @Configuration} classes) and the property
         * placeholder configurers. Other post-processors are left out, because they can create
         * beans (EhcacheManagerBeanConfigurer starts the cache manager, for example).
         */
        public void loadDefinitions() {
            prepareRefresh();
            final ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();
            prepareBeanFactory(beanFactory);
            postProcessBeanFactory(beanFactory);

            final Set<String> done = new HashSet<>();
            boolean found = true;
            while (found) {
                found = false;
                for (final String name :
                        beanFactory.getBeanNamesForType(
                                BeanDefinitionRegistryPostProcessor.class, true, false)) {
                    if (done.add(name)) {
                        found = true;
                        final BeanDefinitionRegistryPostProcessor processor =
                                beanFactory.getBean(
                                        name, BeanDefinitionRegistryPostProcessor.class);
                        // Only the registry phase: postProcessBeanFactory would CGLIB-enhance the
                        // @Configuration classes, which needs --add-opens on JDK 17+ and adds
                        // nothing to the definitions
                        processor.postProcessBeanDefinitionRegistry(
                                (BeanDefinitionRegistry) beanFactory);
                    }
                }
            }
            for (final String name :
                    beanFactory.getBeanNamesForType(
                            PlaceholderConfigurerSupport.class, true, false)) {
                beanFactory
                        .getBean(name, PlaceholderConfigurerSupport.class)
                        .postProcessBeanFactory(beanFactory);
            }
            // As refresh() does: the placeholder configurers changed the original definitions
            beanFactory.clearMetadataCache();
        }
    }

    private final ConfigurableListableBeanFactory beanFactory;
    private final ClassLoader classLoader;
    private final String contextName;
    private final List<String> problems = new ArrayList<>();

    private SpringXmlContextValidator(
            String contextName, ConfigurableListableBeanFactory beanFactory) {
        this.contextName = contextName;
        this.beanFactory = beanFactory;
        this.classLoader =
                beanFactory.getBeanClassLoader() != null
                        ? beanFactory.getBeanClassLoader()
                        : ClassUtils.getDefaultClassLoader();
    }

    /**
     * Check every bean definition registered in the given (already loaded) context.
     *
     * @return one line per problem, empty if none
     */
    public static List<String> validate(
            String contextName, ConfigurableListableBeanFactory beanFactory) {
        final SpringXmlContextValidator validator =
                new SpringXmlContextValidator(contextName, beanFactory);
        for (final String beanName : beanFactory.getBeanDefinitionNames()) {
            final BeanDefinition definition;
            try {
                definition = beanFactory.getMergedBeanDefinition(beanName);
            } catch (RuntimeException e) {
                validator.problem(beanName, "cannot merge with its parent definition: " + e);
                continue;
            }
            validator.checkDefinition(beanName, definition);
        }
        return validator.problems;
    }

    private void problem(String beanName, String message) {
        problems.add(contextName + ": bean '" + beanName + "': " + message);
    }

    private void checkDefinition(String beanName, BeanDefinition definition) {
        if (!(definition instanceof AbstractBeanDefinition)) {
            return;
        }
        final AbstractBeanDefinition bd = (AbstractBeanDefinition) definition;

        // References are checked even for abstract definitions: their children inherit them
        checkValues(beanName, bd);
        if (bd.isAbstract()) {
            return;
        }

        Class<?> beanClass = null;
        if (bd.getBeanClassName() != null) {
            try {
                beanClass = bd.resolveBeanClass(classLoader);
            } catch (ClassNotFoundException | LinkageError e) {
                problem(beanName, "class " + bd.getBeanClassName() + " cannot be loaded: " + e);
                return;
            }
        }

        final Class<?> targetClass = checkInstantiation(beanName, bd, beanClass);
        if (targetClass == null) {
            return;
        }
        checkProperties(beanName, bd.getPropertyValues(), targetClass);
        checkMethod(beanName, "init-method", bd.getInitMethodName(), targetClass);
        if (!AbstractBeanDefinition.INFER_METHOD.equals(bd.getDestroyMethodName())) {
            checkMethod(beanName, "destroy-method", bd.getDestroyMethodName(), targetClass);
        }
        for (final MethodOverride override : bd.getMethodOverrides().getOverrides()) {
            if (override instanceof LookupOverride
                    && ReflectionUtils.findMethod(targetClass, override.getMethodName()) == null
                    && !hasMethodNamed(targetClass, override.getMethodName())) {
                problem(
                        beanName,
                        "lookup-method "
                                + override.getMethodName()
                                + " not found on "
                                + targetClass.getName());
            }
        }
    }

    /**
     * Check that the definition can be instantiated and return the class whose properties are set,
     * or {@code null} if that can't be determined statically.
     */
    private Class<?> checkInstantiation(
            String beanName, AbstractBeanDefinition bd, Class<?> beanClass) {
        final int argCount = argumentCount(bd.getConstructorArgumentValues());
        final boolean autowiredArgs =
                bd.getResolvedAutowireMode() == AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR;

        final String factoryMethod = bd.getFactoryMethodName();
        if (factoryMethod == null) {
            if (beanClass == null) {
                problem(beanName, "has neither a class nor a factory method");
                return null;
            }
            // Without explicit arguments Spring may autowire an annotated or single constructor
            if (argCount > 0 && !hasConstructor(beanClass, argCount, autowiredArgs)) {
                problem(
                        beanName,
                        "no constructor of "
                                + beanClass.getName()
                                + " takes "
                                + argCount
                                + " argument(s)");
            }
            return beanClass;
        }

        final Class<?> factoryClass;
        final boolean isStatic;
        if (bd.getFactoryBeanName() != null) {
            factoryClass = beanTypeOf(bd.getFactoryBeanName());
            isStatic = false;
        } else {
            factoryClass = beanClass;
            isStatic = true;
        }
        if (factoryClass == null) {
            // A factory whose type depends on another factory: nothing more to check statically
            return null;
        }
        Class<?> returnType = null;
        for (final Method method : ReflectionUtils.getUniqueDeclaredMethods(factoryClass)) {
            if (method.getName().equals(factoryMethod)
                    && (!isStatic || Modifier.isStatic(method.getModifiers()))
                    && (autowiredArgs
                            ? method.getParameterCount() >= argCount
                            : method.getParameterCount() == argCount || method.isVarArgs())) {
                returnType = method.getReturnType();
            }
        }
        if (returnType == null) {
            problem(
                    beanName,
                    "no "
                            + (isStatic ? "static " : "")
                            + "factory method "
                            + factoryMethod
                            + " with "
                            + argCount
                            + " argument(s) on "
                            + factoryClass.getName());
            return null;
        }
        // Properties are set on the returned object, whose concrete type may be narrower
        return returnType.isInterface() || returnType == Object.class ? null : returnType;
    }

    /** The class of a named bean, or the class its factory method returns. */
    private Class<?> beanTypeOf(String beanName) {
        BeanFactory factory = beanFactory;
        while (factory != null) {
            if (factory instanceof ConfigurableListableBeanFactory
                    && ((ConfigurableListableBeanFactory) factory)
                            .containsBeanDefinition(beanName)) {
                final BeanDefinition definition =
                        ((ConfigurableListableBeanFactory) factory)
                                .getMergedBeanDefinition(beanName);
                if (definition.getFactoryMethodName() != null
                        || definition.getBeanClassName() == null) {
                    return null;
                }
                try {
                    final Class<?> type =
                            ClassUtils.forName(definition.getBeanClassName(), classLoader);
                    return FactoryBean.class.isAssignableFrom(type) ? null : type;
                } catch (ClassNotFoundException | LinkageError e) {
                    return null;
                }
            }
            factory =
                    factory instanceof HierarchicalBeanFactory
                            ? ((HierarchicalBeanFactory) factory).getParentBeanFactory()
                            : null;
        }
        return null;
    }

    private static int argumentCount(ConstructorArgumentValues values) {
        int count = values.getGenericArgumentValues().size();
        for (final Integer index : values.getIndexedArgumentValues().keySet()) {
            count = Math.max(count, index + 1);
        }
        return count;
    }

    private static boolean hasConstructor(Class<?> type, int argCount, boolean autowired) {
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            // Only possible with method injection (CGLIB subclass), checked elsewhere
            return true;
        }
        for (final Constructor<?> constructor : type.getDeclaredConstructors()) {
            final int count = constructor.getParameterCount();
            if (autowired ? count >= argCount : count == argCount || constructor.isVarArgs()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMethodNamed(Class<?> type, String name) {
        for (final Method method : ReflectionUtils.getUniqueDeclaredMethods(type)) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private void checkMethod(String beanName, String kind, String methodName, Class<?> type) {
        if (methodName != null
                && !methodName.isEmpty()
                && ReflectionUtils.findMethod(type, methodName) == null) {
            problem(beanName, kind + " " + methodName + "() not found on " + type.getName());
        }
    }

    private void checkProperties(
            String beanName, MutablePropertyValues values, Class<?> targetClass) {
        for (final PropertyValue value : values.getPropertyValueList()) {
            final String path = value.getName();
            Class<?> type = targetClass;
            final String[] segments = path.split("\\.");
            for (int i = 0; i < segments.length && type != null; i++) {
                final String segment = segments[i].replaceAll("\\[.*\\]$", "");
                final PropertyDescriptor descriptor =
                        BeanUtils.getPropertyDescriptor(type, segment);
                final boolean last = i == segments.length - 1 && !segments[i].endsWith("]");
                if (descriptor == null
                        || (last
                                ? descriptor.getWriteMethod() == null
                                : descriptor.getReadMethod() == null)) {
                    problem(
                            beanName,
                            "property '"
                                    + path
                                    + "' is not "
                                    + (last ? "writable" : "readable")
                                    + " on "
                                    + type.getName());
                    break;
                }
                type = last ? null : descriptor.getPropertyType();
                if (type != null && (type.isInterface() || type == Object.class)) {
                    // Nested paths through an interface depend on the runtime type
                    break;
                }
            }
        }
    }

    /** Check references and inner beans in property values and constructor arguments. */
    private void checkValues(String beanName, AbstractBeanDefinition bd) {
        for (final PropertyValue value : bd.getPropertyValues().getPropertyValueList()) {
            checkValue(beanName, value.getValue());
        }
        final ConstructorArgumentValues args = bd.getConstructorArgumentValues();
        for (final ConstructorArgumentValues.ValueHolder holder :
                args.getIndexedArgumentValues().values()) {
            checkValue(beanName, holder.getValue());
        }
        for (final ConstructorArgumentValues.ValueHolder holder : args.getGenericArgumentValues()) {
            checkValue(beanName, holder.getValue());
        }
    }

    private void checkValue(String beanName, Object value) {
        if (value instanceof RuntimeBeanReference) {
            checkReference(beanName, ((RuntimeBeanReference) value).getBeanName());
        } else if (value instanceof RuntimeBeanNameReference) {
            checkReference(beanName, ((RuntimeBeanNameReference) value).getBeanName());
        } else if (value instanceof BeanDefinitionHolder) {
            final BeanDefinitionHolder holder = (BeanDefinitionHolder) value;
            checkInner(beanName + " > " + holder.getBeanName(), holder.getBeanDefinition());
        } else if (value instanceof BeanDefinition) {
            checkInner(beanName + " > (inner bean)", (BeanDefinition) value);
        } else if (value instanceof Collection) {
            for (final Object element : (Collection<?>) value) {
                checkValue(beanName, element);
            }
        } else if (value instanceof Map) {
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                checkValue(beanName, entry.getKey());
                checkValue(beanName, entry.getValue());
            }
        }
    }

    private void checkInner(String name, BeanDefinition definition) {
        if (definition.getParentName() != null) {
            try {
                // Same merge that the bean factory does for an inner bean with a parent
                final RootBeanDefinition merged =
                        new RootBeanDefinition(
                                (RootBeanDefinition)
                                        beanFactory.getMergedBeanDefinition(
                                                definition.getParentName()));
                merged.overrideFrom(definition);
                checkDefinition(name, merged);
            } catch (RuntimeException e) {
                problem(name, "cannot merge with parent " + definition.getParentName() + ": " + e);
            }
            return;
        }
        checkDefinition(name, definition);
    }

    private void checkReference(String beanName, String reference) {
        if (!beanFactory.containsBean(reference)) {
            problem(beanName, "references bean '" + reference + "', which is not defined");
        }
    }
}
