/*
 *
 * Copyright 2017 Nitrite author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.dizitart.no2.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.dizitart.no2.exceptions.IndexingException;
import org.dizitart.no2.exceptions.InvalidIdException;
import org.dizitart.no2.exceptions.NotIdentifiableException;
import org.dizitart.no2.mapper.NitriteMapper;
import org.dizitart.no2.objects.*;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.util.*;

import static org.dizitart.no2.exceptions.ErrorCodes.*;
import static org.dizitart.no2.exceptions.ErrorMessage.*;
import static org.dizitart.no2.objects.filters.ObjectFilters.eq;
import static org.dizitart.no2.util.ReflectionUtils.getAnnotationUpto;
import static org.dizitart.no2.util.ReflectionUtils.getField;
import static org.dizitart.no2.util.StringUtils.isNullOrEmpty;
import static org.dizitart.no2.util.ValidationUtils.notNull;
import static org.dizitart.no2.util.ValidationUtils.validateObjectIndexField;

/**
 * A utility class for {@link Object}.
 *
 * @since 1.0
 * @author Anindya Chatterjee.
 */
@UtilityClass
@Slf4j
public class ObjectUtils {

    /**
     * Generates the name of an {@link org.dizitart.no2.objects.ObjectRepository}.
     *
     * @param <T>           the type parameter
     * @param type          the type of object stored in the repository
     * @return the name of the object repository.
     */
    public static <T> String findObjectStoreName(Class<T> type) {
        notNull(type, errorMessage("type can not be null", VE_OBJ_STORE_NULL_TYPE));
        return type.getName();
    }

    /**
     * Extract indices information by scanning for {@link Index} annotated fields.
     *
     * @param <T>           the type parameter
     * @param nitriteMapper the {@link NitriteMapper}
     * @param type          the type of the object stored in the repository
     * @return the set of all {@link Index} annotations found.
     */
    public static <T> Set<Index> extractIndices(NitriteMapper nitriteMapper, Class<T> type) {
        notNull(type, errorMessage("type can not be null", VE_INDEX_ANNOTATION_NULL_TYPE));

        List<Indices> indicesList;
        if (type.isAnnotationPresent(InheritIndices.class)) {
            indicesList = getAnnotationUpto(Indices.class, type, Object.class);
        } else {
            indicesList = new ArrayList<>();
            Indices indices = type.getAnnotation(Indices.class);
            if (indices != null) indicesList.add(indices);
        }

        Set<Index> indexSet = new LinkedHashSet<>();
        if (indicesList != null) {
            for (Indices indices : indicesList) {
                Index[] indexList = indices.value();
                populateIndex(nitriteMapper, type, Arrays.asList(indexList), indexSet);
            }
        }

        List<Index> indexList;
        if (type.isAnnotationPresent(InheritIndices.class)) {
            indexList = getAnnotationUpto(Index.class, type, Object.class);
        } else {
            indexList = new ArrayList<>();
            Index index = type.getAnnotation(Index.class);
            if (index != null) indexList.add(index);
        }

        if (indexList != null) {
            populateIndex(nitriteMapper, type, indexList, indexSet);
        }
        return indexSet;
    }

    /**
     * Gets the field marked with {@link Id} annotation.
     *
     * @param <T>           the type parameter
     * @param nitriteMapper the nitrite mapper
     * @param type          the type
     * @return the id field
     */
    public static <T> Field getIdField(NitriteMapper nitriteMapper, Class<T> type) {
        Field[] fields;
        if (type.isAnnotationPresent(InheritIndices.class)) {
            fields = type.getFields();
        } else {
            fields = type.getDeclaredFields();
        }

        boolean alreadyIdFound = false;
        Field idField = null;
        for (Field field : fields) {
            if (field.isAnnotationPresent(Id.class)) {
                validateObjectIndexField(nitriteMapper, field.getType(), field.getName());
                if (alreadyIdFound) {
                    throw new NotIdentifiableException(OBJ_MULTIPLE_ID_FOUND);
                } else {
                    alreadyIdFound = true;
                    idField = field;
                }
            }
        }
        return idField;
    }

    /**
     * Creates unique filter from the object.
     *
     * @param object  the object
     * @param idField the id field
     * @return the equals filter
     */
    public static ObjectFilter createUniqueFilter(Object object, Field idField) {
        idField.setAccessible(true);
        try {
            Object value = idField.get(object);
            if (value == null) {
                throw new InvalidIdException(ID_FILTER_VALUE_CAN_NOT_BE_NULL);
            }
            return eq(idField.getName(), value);
        } catch (IllegalAccessException iae) {
            throw new InvalidIdException(ID_FIELD_IS_NOT_ACCESSIBLE);
        }
    }

    /**
     * Checks whether a collection name is a valid object store name.
     *
     * @param collectionName the collection name
     * @return `true` if it is a valid object store name; `false` otherwise.
     */
    public static boolean isObjectStore(String collectionName) {
        try {
            if (isNullOrEmpty(collectionName)) return false;
            Class clazz = Class.forName(collectionName);
            return clazz != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static <T> T newInstance(Class<T> type) {
        try {
            return type.newInstance();
        } catch (Exception e) {
            return new ObjenesisStd().newInstance(type);
        }
    }

    private <T> void populateIndex(NitriteMapper nitriteMapper, Class<T> type,
                                   List<Index> indexList, Set<Index> indexSet) {
        for (Index index : indexList) {
            String name = index.value();
            Field field = getField(type, name);
            if (field != null) {
                validateObjectIndexField(nitriteMapper, field.getType(), field.getName());
                indexSet.add(index);
            } else {
                throw new IndexingException(errorMessage(
                        "field " + name + " does not exists for type " + type.getName(),
                        IE_OBJ_INDEX_INVALID_FIELD));
            }
        }
    }
}
