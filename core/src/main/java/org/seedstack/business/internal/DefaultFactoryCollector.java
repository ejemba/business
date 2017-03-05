/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.business.internal;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Key;
import com.google.inject.util.Types;
import org.seedstack.business.domain.AggregateRoot;
import org.seedstack.business.domain.Factory;
import org.seedstack.business.domain.ValueObject;
import org.seedstack.seed.core.internal.guice.BindingStrategy;
import org.seedstack.seed.core.internal.guice.GenericBindingStrategy;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;


class DefaultFactoryCollector {
    private final Map<Key<?>, Class<?>> bindings;

    DefaultFactoryCollector(Map<Key<?>, Class<?>> bindings) {
        this.bindings = bindings;
    }

    Collection<BindingStrategy> collect(Collection<Class<? extends AggregateRoot>> aggregateClasses,
                                        Collection<Class<? extends ValueObject>> valueObjectClasses) {
        Collection<BindingStrategy> strategies = new ArrayList<>();
        boolean bindGuiceFactory = true;

        if (!aggregateClasses.isEmpty() || !valueObjectClasses.isEmpty()) {
            strategies.add(new GenericBindingStrategy<>(
                    Factory.class,
                    DefaultFactory.class,
                    Stream.concat(aggregateClasses.stream(), valueObjectClasses.stream())
                            .filter(this::isaBoolean)
                            .map(candidate -> new Type[]{candidate})
                            .collect(Collectors.toList())
            ));
            bindGuiceFactory = false;
        }

        Multimap<Type, Class<?>> producibleClasses = filterProducibleClasses(bindings);
        if (!producibleClasses.isEmpty()) {
            strategies.add(new DefaultFactoryBindingStrategy<>(
                    Factory.class,
                    DefaultFactory.class,
                    producibleClasses,
                    bindGuiceFactory
            ));
        }

        return strategies;
    }

    /**
     * Filter a map containing pairs of interface/implementation in order to get only producible classes.
     *
     * @param bindings map of interface/implementation
     * @return producible pairs
     */
    private Multimap<Type, Class<?>> filterProducibleClasses(Map<Key<?>, Class<?>> bindings) {
        Multimap<Type, Class<?>> defaultFactoryToBind = ArrayListMultimap.create();
        bindings.entrySet().stream()
                .filter(entry -> isaBoolean(entry.getKey().getTypeLiteral().getType()))
                .forEach(entry -> defaultFactoryToBind.put(entry.getKey().getTypeLiteral().getType(), entry.getValue()));
        return defaultFactoryToBind;
    }

    private boolean isaBoolean(Type type) {
        boolean result = false;
        if (type instanceof Class<?>) {
            result = BusinessSpecifications.PRODUCIBLE.isSatisfiedBy((Class<?>) type);
        } else if (type instanceof ParameterizedType) {
            result = BusinessSpecifications.PRODUCIBLE.isSatisfiedBy((Class<?>) ((ParameterizedType) type).getRawType());
        }
        return result && !bindings.containsKey(Key.get(Types.newParameterizedType(Factory.class, type)));
    }
}
