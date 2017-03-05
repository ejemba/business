/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.business.internal;

import com.google.inject.Key;
import io.nuun.kernel.api.plugin.InitState;
import io.nuun.kernel.api.plugin.context.InitContext;
import io.nuun.kernel.api.plugin.request.ClasspathScanRequest;
import io.nuun.kernel.api.plugin.request.ClasspathScanRequestBuilder;
import org.kametic.specifications.Specification;
import org.seedstack.business.assembler.Assembler;
import org.seedstack.business.domain.AggregateRoot;
import org.seedstack.business.domain.Factory;
import org.seedstack.business.domain.Repository;
import org.seedstack.business.domain.ValueObject;
import org.seedstack.seed.core.internal.AbstractSeedPlugin;
import org.seedstack.seed.core.internal.guice.BindingStrategy;
import org.seedstack.seed.core.internal.guice.BindingUtils;
import org.seedstack.seed.core.internal.utils.SpecificationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.seedstack.business.internal.utils.BusinessUtils.streamClasses;
import static org.seedstack.shed.reflect.ClassPredicates.classIs;
import static org.seedstack.shed.reflect.ClassPredicates.classIsDescendantOf;
import static org.seedstack.shed.reflect.ClassPredicates.classIsInterface;
import static org.seedstack.shed.reflect.ClassPredicates.classModifierIs;

/**
 * This plugin is a multi round plugin.
 * <p>
 * It uses two round because it needs to scan user interfaces, for instance those annotated with {@code @Finder}.
 * Then in the second round, it scan the implementations of the scanned interfaces.
 * </p>
 * This plugin also bind default implementation for repository, factory and assembler. For this, it uses the
 * {@link BindingStrategy}.
 */
public class BusinessPlugin extends AbstractSeedPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger(BusinessPlugin.class);
    private final Collection<Class<? extends AggregateRoot>> aggregateClasses = new HashSet<>();
    private final Collection<Class<? extends ValueObject>> valueObjectClasses = new HashSet<>();
    private final Collection<Class<? extends Assembler>> assemblerClasses = new HashSet<>();
    private final Collection<Class<? extends Repository>> repositoriesInterfaces = new HashSet<>();
    private final Collection<Class<? extends Factory>> factoryInterfaces = new HashSet<>();
    private final Collection<Class<?>> serviceInterfaces = new HashSet<>();
    private final Collection<Class<?>> policyInterfaces = new HashSet<>();
    private final Collection<Class<?>> finderInterfaces = new HashSet<>();
    private final Collection<Class<?>> dtoOfClasses = new HashSet<>();
    private final Collection<Class<? extends Assembler>> defaultAssemblerClasses = new HashSet<>();
    private final Collection<Class<? extends Repository>> defaultRepositoryClasses = new HashSet<>();
    private final Map<Class<?>, Specification<Class<?>>> specsByInterfaceMap = new HashMap<>();
    private final Map<Key<?>, Class<?>> bindings = new HashMap<>();
    private final Collection<BindingStrategy> bindingStrategies = new ArrayList<>();

    @Override
    public String name() {
        return "business-core";
    }

    @Override
    public Collection<ClasspathScanRequest> classpathScanRequests() {
        if (round.isFirst()) {
            return classpathScanRequestBuilder()
                    .specification(BusinessSpecifications.AGGREGATE_ROOT)
                    .specification(BusinessSpecifications.VALUE_OBJECT)
                    .specification(BusinessSpecifications.CLASSIC_ASSEMBLER)
                    .specification(BusinessSpecifications.SERVICE)
                    .specification(BusinessSpecifications.FACTORY)
                    .specification(BusinessSpecifications.FINDER)
                    .specification(BusinessSpecifications.POLICY)
                    .specification(BusinessSpecifications.REPOSITORY)
                    .specification(BusinessSpecifications.DEFAULT_ASSEMBLER)
                    .specification(BusinessSpecifications.DEFAULT_REPOSITORY)
                    .specification(BusinessSpecifications.DTO_OF)
                    .specification(BusinessSpecifications.IDENTITY_HANDLER)
                    .build();
        } else {
            ClasspathScanRequestBuilder classpathScanRequestBuilder = classpathScanRequestBuilder();
            classpathRequestForDescendantTypesOf(classpathScanRequestBuilder, factoryInterfaces);
            classpathRequestForDescendantTypesOf(classpathScanRequestBuilder, serviceInterfaces);
            classpathRequestForDescendantTypesOf(classpathScanRequestBuilder, finderInterfaces);
            classpathRequestForDescendantTypesOf(classpathScanRequestBuilder, policyInterfaces);
            classpathRequestForDescendantTypesOf(classpathScanRequestBuilder, repositoriesInterfaces);
            return classpathScanRequestBuilder.build();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public InitState initialize(InitContext initContext) {
        // The first round is used to scan interfaces
        if (round.isFirst()) {
            streamClasses(initContext, BusinessSpecifications.AGGREGATE_ROOT, AggregateRoot.class).forEach(aggregateClasses::add);
            LOGGER.debug("Aggregate roots => {}", aggregateClasses);

            streamClasses(initContext, BusinessSpecifications.VALUE_OBJECT, ValueObject.class).forEach(valueObjectClasses::add);
            LOGGER.debug("Value objects => {}", valueObjectClasses);

            streamClasses(initContext, BusinessSpecifications.CLASSIC_ASSEMBLER, Assembler.class).forEach(assemblerClasses::add);
            LOGGER.debug("Assembler classes => {}", assemblerClasses);

            streamClasses(initContext, BusinessSpecifications.REPOSITORY, Repository.class).forEach(repositoriesInterfaces::add);
            LOGGER.debug("Repository interfaces => {}", repositoriesInterfaces);

            streamClasses(initContext, BusinessSpecifications.FACTORY, Factory.class).forEach(factoryInterfaces::add);
            LOGGER.debug("Factory interfaces => {}", factoryInterfaces);

            streamClasses(initContext, BusinessSpecifications.SERVICE, Object.class).forEach(serviceInterfaces::add);
            LOGGER.debug("Domain service interfaces => {}", serviceInterfaces);

            streamClasses(initContext, BusinessSpecifications.FINDER, Object.class).forEach(finderInterfaces::add);
            LOGGER.debug("Finder interfaces => {}", finderInterfaces);

            streamClasses(initContext, BusinessSpecifications.POLICY, Object.class).forEach(policyInterfaces::add);
            LOGGER.debug("Policy interfaces => {}", policyInterfaces);

            streamClasses(initContext, BusinessSpecifications.DTO_OF, Object.class).forEach(dtoOfClasses::add);
            LOGGER.debug("DtoOf classes => {}", dtoOfClasses);

            streamClasses(initContext, BusinessSpecifications.DEFAULT_REPOSITORY, Repository.class).forEach(defaultRepositoryClasses::add);
            LOGGER.debug("Default repositories => {}", defaultRepositoryClasses);

            streamClasses(initContext, BusinessSpecifications.DEFAULT_ASSEMBLER, Assembler.class).forEach(defaultAssemblerClasses::add);
            LOGGER.debug("Default assemblers => {}", defaultAssemblerClasses);

            return InitState.NON_INITIALIZED;
        } else {
            // The second round is used to scan implementations of the previously scanned interfaces

            // Classic bindings
            // -- add assemblers to the default mode even if they have no client user interfaces
            List<Class<?>> assemblerClass = new ArrayList<>();
            assemblerClass.add(Assembler.class);
            specsByInterfaceMap.put(Assembler.class, BusinessSpecifications.CLASSIC_ASSEMBLER);

            bindings.putAll(associatesInterfaceToImplementations(initContext, factoryInterfaces));
            bindings.putAll(associatesInterfaceToImplementations(initContext, serviceInterfaces));
            bindings.putAll(associatesInterfaceToImplementations(initContext, finderInterfaces));
            bindings.putAll(associatesInterfaceToImplementations(initContext, policyInterfaces));
            bindings.putAll(associatesInterfaceToImplementations(initContext, repositoriesInterfaces));
            bindings.putAll(associatesInterfaceToImplementations(initContext, assemblerClass));

            // Bindings for default repositories
            bindingStrategies.addAll(new DefaultRepositoryCollector(defaultRepositoryClasses, getApplication()).collect(aggregateClasses));

            // Bindings for default factories
            bindingStrategies.addAll(new DefaultFactoryCollector(bindings).collect(aggregateClasses, valueObjectClasses));

            // Bindings for default assemblers
            bindingStrategies.addAll(new DefaultAssemblerCollector(defaultAssemblerClasses).collect(dtoOfClasses));

            return InitState.INITIALIZED;
        }
    }

    @Override
    public Object nativeUnitModule() {
        return new BusinessModule(assemblerClasses, bindings, bindingStrategies);
    }

    /**
     * Builds a ClasspathScanRequest to find all the descendant of the given interfaces.
     *
     * @param interfaces the interfaces
     */
    private <T extends Class<?>> void classpathRequestForDescendantTypesOf(ClasspathScanRequestBuilder classpathScanRequestBuilder, Collection<T> interfaces) {
        for (Class<?> anInterface : interfaces) {
            LOGGER.trace("Request implementations of: {}", anInterface.getName());
            Specification<Class<?>> spec = new SpecificationBuilder<>(classIsDescendantOf(anInterface).and(classIsInterface().negate()).and(classModifierIs(Modifier.ABSTRACT).negate())).build();
            classpathScanRequestBuilder = classpathScanRequestBuilder.specification(spec);
            specsByInterfaceMap.put(anInterface, spec);
        }
    }

    /**
     * Associates scanned interfaces to their implementations. It also handles qualified bindings in the case where
     * there is multiple implementation for the same interface.
     * <p>
     * This is the "default mode" for binding in the business framework.
     * </p>
     *
     * @param initContext the context containing the implementations
     * @param interfaces  the interfaces to bind
     * @return the map of interface/implementation to bind
     * @see BindingUtils#resolveBindingDefinitions(Class, Class, Class[])
     */
    @SuppressWarnings("unchecked")
    private <T extends Class> Map<Key<T>, T> associatesInterfaceToImplementations(InitContext initContext, Collection<T> interfaces) {
        Map<Key<T>, T> keyMap = new HashMap<>();
        for (Class<?> anInterface : interfaces) {
            Collection<Class<?>> subTypes = initContext.scannedTypesBySpecification().get(specsByInterfaceMap.get(anInterface));
            keyMap.putAll(BindingUtils.resolveBindingDefinitions((T) anInterface, subTypes));
        }
        return keyMap;
    }
}
