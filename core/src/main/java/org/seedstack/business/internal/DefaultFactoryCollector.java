/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.business.internal;

import org.seedstack.business.Producible;
import org.seedstack.business.domain.Factory;
import org.seedstack.business.internal.strategy.FactoryPatternBindingStrategy;
import org.seedstack.seed.core.internal.guice.BindingStrategy;
import org.seedstack.seed.core.internal.guice.GenericBindingStrategy;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;


class DefaultFactoryCollector {
    private final Collection<Class<? extends Factory>> explicitFactories;

    DefaultFactoryCollector(Collection<Class<? extends Factory>> explicitFactories) {
        this.explicitFactories = explicitFactories;
    }

    public Collection<BindingStrategy> collect(Collection<Class<? extends Producible>> producibleClasses) {
        System.out.println(explicitFactories);
        System.out.println(producibleClasses);
        throw new RuntimeException("yop");
    }
}
