package com.canreadit.shared.internal;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.RecordComponent;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Our DTO records live in {@code @NullMarked} packages: every component is non-null unless it is
 * annotated {@code @Nullable}. This converter writes that into the OpenAPI schema (required list,
 * and {@code null} as an allowed type) so generated clients get exact types.
 */
@Component
class NullnessModelConverter implements ModelConverter {

    @Override
    public @Nullable Schema<?> resolve(
            AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (!chain.hasNext()) {
            return null;
        }
        Schema<?> resolved = chain.next().resolve(type, context, chain);
        // swagger-core describes types with Jackson 2's JavaType; this is its API, not ours.
        Class<?> raw = Json.mapper().constructType(type.getType()).getRawClass();
        if (resolved == null || !raw.isRecord() || !raw.getPackageName().startsWith("com.canreadit")) {
            return resolved;
        }
        Schema<?> model = resolved.get$ref() == null
                ? resolved
                : context.getDefinedModels()
                        .get(resolved.get$ref().substring(resolved.get$ref().lastIndexOf('/') + 1));
        if (model == null || model.getProperties() == null) {
            return resolved;
        }
        for (RecordComponent component : raw.getRecordComponents()) {
            String name = component.getName();
            Schema<?> property = model.getProperties().get(name);
            if (property == null) {
                continue;
            }
            if (component.getAnnotatedType().isAnnotationPresent(Nullable.class)) {
                model.getProperties().put(name, nullable(property));
            } else if (model.getRequired() == null || !model.getRequired().contains(name)) {
                model.addRequiredItem(name);
            }
        }
        return resolved;
    }

    private static Schema<?> nullable(Schema<?> property) {
        if (property.get$ref() != null) {
            if (property.getAnyOf() != null) {
                return property; // already wrapped
            }
            return new JsonSchema().anyOf(List.of(new JsonSchema().$ref(property.get$ref()), nullType()));
        }
        Set<String> types = new LinkedHashSet<>();
        if (property.getTypes() != null) {
            types.addAll(property.getTypes());
        } else if (property.getType() != null) {
            types.add(property.getType());
        }
        types.add("null");
        property.setTypes(types);
        return property;
    }

    private static Schema<?> nullType() {
        return new JsonSchema().typesItem("null");
    }
}
