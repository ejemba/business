/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.business.internal;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import org.seedstack.business.assembler.Assembler;
import org.seedstack.business.assembler.FluentAssembler;
import org.seedstack.business.domain.DomainRegistry;
import org.seedstack.business.internal.assembler.dsl.FluentAssemblerImpl;
import org.seedstack.business.internal.assembler.dsl.InternalRegistry;
import org.seedstack.business.internal.assembler.dsl.InternalRegistryInternal;
import org.seedstack.seed.core.internal.guice.BindingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

class BusinessModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(BusinessModule.class);
    private final Map<Key<?>, Class<?>> bindings;
    private final Collection<Class<? extends Assembler>> assemblersClasses;
    private final Collection<BindingStrategy> bindingStrategies;

    BusinessModule(Collection<Class<? extends Assembler>> assemblersClasses, Map<Key<?>, Class<?>> bindings, Collection<BindingStrategy> bindingStrategies) {
        this.assemblersClasses = assemblersClasses;
        this.bindings = bindings;
        this.bindingStrategies = bindingStrategies;
    }

    @Override
    protected void configure() {
        bind(FluentAssembler.class).to(FluentAssemblerImpl.class);
        bind(InternalRegistry.class).to(InternalRegistryInternal.class);
        bind(DomainRegistry.class).to(DomainRegistryImpl.class);

        for (Entry<Key<?>, Class<?>> binding : bindings.entrySet()) {
            LOGGER.trace("Binding {} to {}", binding.getKey(), binding.getValue().getSimpleName());
            bind(binding.getKey()).to((Class) binding.getValue());
        }

        // Bind assembler implementations
        for (Class<?> assembler : assemblersClasses) {
            bind(assembler);
        }

        // Bind strategies
        for (BindingStrategy bindingStrategy : bindingStrategies) {
            bindingStrategy.resolve(binder());
        }
    }
}
