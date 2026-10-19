package com.dispatch.service.impl;

import com.dispatch.exception.DuplicateIdException;
import org.springframework.data.repository.ListCrudRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Shared bulk-upsert logic for entities with client-assigned String IDs.
 * <p>
 * Instead of one SELECT per row (what {@code save()} does for assigned IDs), existing rows are
 * fetched with chunked {@code WHERE id IN (...)} queries. New entities are then batch-INSERTed and
 * loaded ones are batch-UPDATEd through dirty checking when the transaction flushes.
 */
final class UpsertSupport {

    /** Keeps IN-lists well below database parameter limits (PostgreSQL allows 32,767). */
    static final int ID_LOOKUP_CHUNK_SIZE = 1_000;

    private UpsertSupport() {
    }

    /**
     * Rejects requests that repeat an ID, then returns entities ready for {@code saveAll}:
     * updated managed instances for IDs that already exist and new instances for the rest.
     *
     * @param requests      validated request items
     * @param requestId     extracts the ID from a request item
     * @param idField       name of the ID field, used in error messages
     * @param repository    repository used for the bulk existence lookup
     * @param entityId      extracts the ID from an entity
     * @param createEntity  builds a new entity from a request item
     * @param updateEntity  copies a request item onto an existing entity
     */
    static <R, E> List<E> prepareUpsert(List<R> requests,
                                        Function<R, String> requestId,
                                        String idField,
                                        ListCrudRepository<E, String> repository,
                                        Function<E, String> entityId,
                                        Function<R, E> createEntity,
                                        BiConsumer<E, R> updateEntity) {
        List<String> ids = requests.stream().map(requestId).toList();
        requireUniqueIds(ids, idField);

        Map<String, E> existing = findExisting(repository, ids, entityId);
        List<E> entities = new ArrayList<>(requests.size());
        for (R request : requests) {
            E entity = existing.get(requestId.apply(request));
            if (entity == null) {
                entity = createEntity.apply(request);
            } else {
                updateEntity.accept(entity, request);
            }
            entities.add(entity);
        }
        return entities;
    }

    static void requireUniqueIds(List<String> ids, String idField) {
        Set<String> seen = new HashSet<>(ids.size() * 2);
        Set<String> duplicates = new LinkedHashSet<>();
        for (String id : ids) {
            if (!seen.add(id)) {
                duplicates.add(id);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new DuplicateIdException(idField, List.copyOf(duplicates));
        }
    }

    private static <E> Map<String, E> findExisting(ListCrudRepository<E, String> repository,
                                                   List<String> ids,
                                                   Function<E, String> entityId) {
        Map<String, E> existing = new HashMap<>();
        for (int from = 0; from < ids.size(); from += ID_LOOKUP_CHUNK_SIZE) {
            List<String> chunk = ids.subList(from, Math.min(from + ID_LOOKUP_CHUNK_SIZE, ids.size()));
            for (E entity : repository.findAllById(chunk)) {
                existing.put(entityId.apply(entity), entity);
            }
        }
        return existing;
    }
}
