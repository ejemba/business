/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.business.internal.assembler.dsl;

import org.javatuples.Triplet;
import org.javatuples.Tuple;
import org.seedstack.business.internal.Tuples;
import org.seedstack.business.domain.AggregateRoot;
import org.seedstack.business.domain.Factory;
import org.seedstack.business.domain.Repository;
import org.seedstack.business.assembler.Assembler;
import org.seedstack.business.assembler.dsl.AggregateNotFoundException;
import org.seedstack.business.assembler.dsl.MergeTuplesWithRepositoryThenFactoryProvider;
import org.seedstack.business.assembler.dsl.MergeTuplesWithRepositoryProvider;
import org.seedstack.business.internal.assembler.dsl.resolver.ParameterHolder;

import java.util.ArrayList;
import java.util.List;


public class MergeMergeTuplesWithRepositoryProviderImpl<T extends Tuple> extends BaseAggAssemblerWithRepoProviderImpl implements MergeTuplesWithRepositoryProvider<T>, MergeTuplesWithRepositoryThenFactoryProvider<T> {

    private final List<Class<? extends AggregateRoot<?>>> aggregateClasses;
    private final List<?> dtos;

    public MergeMergeTuplesWithRepositoryProviderImpl(AssemblerDslContext context, List<Class<? extends AggregateRoot<?>>> aggregateClasses, List<?> dtos) {
        super(context);
        this.aggregateClasses = aggregateClasses;
        this.dtos = dtos;
    }

    // --------------------------- TupleAggAssemblerWithRepoProvider

    @Override
    public MergeTuplesWithRepositoryThenFactoryProvider<T> fromRepository() {
        // it just redirect to the good interface
        return this;
    }


    @Override
    @SuppressWarnings("unchecked")
    public List<T> fromFactory() {
        List<T> aggregateRootTuples = new ArrayList<>(dtos.size());
        for (Object dto : dtos) {
            List<Object> aggregateRoots = new ArrayList<>();
            ParameterHolder parameterHolder = dtoInfoResolver.resolveAggregate(dto);
            int aggregateIndex = 0;
            for (Object o : aggregateClasses) {
                if (o instanceof Class<?>) {
                    Class<? extends AggregateRoot<?>> aggregateClass = (Class<? extends AggregateRoot<?>>) o;
                    Factory<?> factory = context.genericFactoryOf(aggregateClass);
                    Object aggregate = getAggregateFromFactory(factory, aggregateClass, parameterHolder.parametersOfAggregateRoot(aggregateIndex));
                    aggregateRoots.add(aggregate);
                } else {
                    // TODO replace by a seed exception
                    throw new IllegalArgumentException(o + " should be a class. the .to(Tuple aggregateClasses) method only accepts tuple of aggregate root classes.");
                }
                aggregateIndex++;
            }

            aggregateRootTuples.add(assembleWithDto(dto, aggregateRoots));
        }
        return aggregateRootTuples;
    }

    // ---------------------------  TupleAggsAssemblerWithRepoAndFactProvider

    @Override
    public List<T> orFail() throws AggregateNotFoundException {
        List<T> aggregateRootTuples = new ArrayList<>(dtos.size());
        for (Object dto : dtos) {
            // list of triplet - each triplet contains the aggregate root instance, its class and its id (useful if the AR is null).
            List<Triplet<Object, Class<?>, Object>> aggregateRootsMetadata = loadFromRepository(dto);

            boolean shouldThrow = false;

            List<AggregateRoot<?>> aggregateRoots = new ArrayList<>();

            StringBuilder stringBuilder = new StringBuilder().append("Unable to load ");
            for (Triplet<Object, Class<?>, Object> triplet : aggregateRootsMetadata) {

                if (triplet.getValue0() == null) {
                    // If at least one aggregate root is null we throw a AggregateNotFoundException
                    shouldThrow = true;
                    stringBuilder.append("aggregate: ").append(triplet.getValue1()).append(" for id: ").append(triplet.getValue2());
                } else {
                    aggregateRoots.add((AggregateRoot<?>) triplet.getValue0());
                }
            }

            if (shouldThrow) {
                throw new AggregateNotFoundException(stringBuilder.toString());
            }

            aggregateRootTuples.add(assembleWithDto(dto, aggregateRoots));
        }
        return aggregateRootTuples;
    }

    @Override
    public List<T> orFromFactory() {
        boolean atLeastOneAggregateNotFound = false;
        boolean atLeastOneAggregateFound = false;
        List<T> aggregateRootTuples = new ArrayList<>(dtos.size());

        // load from the repository
        for (Object dto : dtos) {
            // list of triplet - each triplet contains the aggregate root instance,
            // its class and its id (useful if the AR is null).
            List<Triplet<Object, Class<?>, Object>> aggregateRootsMetadata = loadFromRepository(dto);

            List<AggregateRoot<?>> aggregateRoots = new ArrayList<>();

            StringBuilder errorMessage = new StringBuilder().append("Unable to load ");
            for (Triplet<Object, Class<?>, Object> triplet : aggregateRootsMetadata) {

                if (triplet.getValue0() == null) {
                    errorMessage.append("aggregate: ").append(triplet.getValue1()).append(" for id: ").append(triplet.getValue2());
                } else {
                    aggregateRoots.add((AggregateRoot<?>) triplet.getValue0());
                }
            }

            if (aggregateRoots.isEmpty()) {
                // No aggregate root persisted -> fallback on factories
                atLeastOneAggregateNotFound = true;
            } else if (aggregateRootsMetadata.size() != aggregateRoots.size()) {
                // data are inconsistent some required aggregate roots are persisted but not all
                throw new IllegalStateException(errorMessage.toString());
            } else {
                // all aggregate roots are loaded -> assemble them and return them
                atLeastOneAggregateFound = true;

                aggregateRootTuples.add(assembleWithDto(dto, aggregateRoots));
            }


            if (atLeastOneAggregateFound && atLeastOneAggregateNotFound) {
                throw new IllegalStateException("State non consistent some aggregate are persisted but not all.");
            }
        }

        if (atLeastOneAggregateNotFound) {
            // Then if none aggregate were persisted, fallback on factory
            return fromFactory();
        } else {
            return aggregateRootTuples;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Triplet<Object, Class<?>, Object>> loadFromRepository(Object dto) {
        Tuple ids = resolveIds(dto, aggregateClasses);

        List<Triplet<Object, Class<?>, Object>> aggregateRoots = new ArrayList<>();
        for (int i = 0; i < ids.getSize(); i++) {
            Class<? extends AggregateRoot<?>> aggregateClass = aggregateClasses.get(i);
            Object id = ids.getValue(i);

            Repository repository = context.repositoryOf(aggregateClass);
            AggregateRoot<?> aggregateRoot = repository.load(id);

            aggregateRoots.add(new Triplet<>(aggregateRoot, aggregateClass, id));
        }

        return aggregateRoots;
    }

    /**
     * Assemble one or a tuple of aggregate root from a dto.
     *
     * @param aggregateRoots the aggregate root(s) to assemble
     * @return the assembled aggregate root(s)
     */
    @SuppressWarnings("unchecked")
    protected T assembleWithDto(Object dto, List<?> aggregateRoots) {
        T aggregateRootTuple = Tuples.create(aggregateRoots);
        Assembler assembler = context.tupleAssemblerOf(aggregateClasses, dto.getClass());
        assembler.mergeAggregateWithDto(aggregateRootTuple, dto);
        return aggregateRootTuple;
    }

}
