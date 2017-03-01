/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.business.internal.utils;

import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import net.jodah.typetools.TypeResolver;
import org.seedstack.business.domain.AggregateRoot;
import org.seedstack.business.domain.BaseAggregateRoot;
import org.seedstack.business.internal.BusinessErrorCode;
import org.seedstack.seed.Application;
import org.seedstack.seed.ClassConfiguration;
import org.seedstack.seed.SeedException;
import org.seedstack.shed.ClassLoaders;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;


public final class BusinessUtils {
    private BusinessUtils() {
        // no instantiation allowed
    }

    public static Class<?> getAggregateIdClass(Class<? extends AggregateRoot<?>> aggregateRootClass) {
        checkNotNull(aggregateRootClass, "aggregateRootClass should not be null");
        return TypeResolver.resolveRawArguments(TypeResolver.resolveGenericType(AggregateRoot.class, aggregateRootClass), aggregateRootClass)[0];
    }

    public static <T> Collection<Class<? extends T>> convertClassCollection(Class<T> target, Collection<Class<?>> collection) {
        return collection.stream().map((Function<Class<?>, Class<? extends T>>) x -> x.asSubclass(target)).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public static Key<?> defaultQualifier(Application application, String key, Class<?> aggregateClass, TypeLiteral<?> genericInterface) {
        Key<?> defaultKey = null;

        ClassConfiguration<?> configuration = application.getConfiguration(aggregateClass);

        if (configuration != null && !configuration.isEmpty()) {
            String qualifierName = configuration.get(key);
            if (qualifierName != null && !"".equals(qualifierName)) {
                try {
                    ClassLoader classLoader = ClassLoaders.findMostCompleteClassLoader(BusinessUtils.class);
                    Class<?> qualifierClass = classLoader.loadClass(qualifierName);
                    if (Annotation.class.isAssignableFrom(qualifierClass)) {
                        defaultKey = Key.get(genericInterface, (Class<? extends Annotation>) qualifierClass);
                    } else {
                        throw SeedException.createNew(BusinessErrorCode.CLASS_IS_NOT_AN_ANNOTATION)
                                .put("aggregateClass", aggregateClass.getName())
                                .put("qualifierClass", qualifierName);
                    }
                } catch (ClassNotFoundException e) {
                    defaultKey = Key.get(genericInterface, Names.named(qualifierName));
                }
            }
        }
        return defaultKey;
    }

    public static Set<Class<?>> includeSuperClasses(Collection<Class<? extends AggregateRoot>> aggregateClasses) {
        Set<Class<?>> results = new HashSet<>();
        for (Class<?> aggregateClass : aggregateClasses) {
            Class<?> classToAdd = aggregateClass;
            while (classToAdd != null) {
                results.add(classToAdd);

                classToAdd = classToAdd.getSuperclass();
                if (BaseAggregateRoot.class.equals(classToAdd) || !BaseAggregateRoot.class.isAssignableFrom(classToAdd)) {
                    break;
                }
            }
        }
        return results;
    }
}
