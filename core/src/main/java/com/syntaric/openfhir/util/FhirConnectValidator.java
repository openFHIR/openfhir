package com.syntaric.openfhir.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.fc.schema.model.FhirConnectModel;
import com.syntaric.openfhir.fc.schema.model.Mapping;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FhirConnectValidator {

    public List<String> validateAgainstContextSchema(final FhirConnectContext parsed) {
        return validateAgainstSchema(parsed, "/contextual-mapping.schema.json");
    }

    public List<String> validateAgainstModelSchema(final FhirConnectModel parsed) {
        return validateAgainstSchema(parsed, "/model-mapping.schema.json");
    }

    public List<String> validateAgainstSchema(final Object parsed, final String schemaName) {
        final ObjectMapper objectMapper = new ObjectMapper();

        final List<String> errors = new ArrayList<>();
        try {
            String schemaString = IOUtils.toString(getClass().getResourceAsStream(schemaName),
                                                   StandardCharsets.UTF_8);

            JsonNode schemaNode = objectMapper.readTree(schemaString);
            JsonNode jsonNode = objectMapper.convertValue(parsed, JsonNode.class);
            JsonSchemaFactory schemaFactory = JsonSchemaFactory.byDefault();
            JsonSchema schema = schemaFactory.getJsonSchema(schemaNode);
            ProcessingReport report = schema.validate(jsonNode);
            if (!report.isSuccess()) {
                report.iterator().forEachRemaining(err -> {
                    if (err.getLogLevel() == LogLevel.ERROR || err.getLogLevel() == LogLevel.FATAL) {
                        if (!err.getMessage().contains("(matched 2 out of 2)")) {
                            // ugly yes, but this error is there because we have a helper method getAttributes to wrap a getAttribute in a list and return it,
                            // which makes this validator think both attributes are present
                            errors.add(err.getMessage());
                        }
                    }
                });
                return errors;
            }
        } catch (final Exception e) {
            log.error("Couldn't validate model mapper against the schema", e);
            throw new IllegalArgumentException("Couldn't validate model mapper against the schema");
        }
        return errors;
    }

    public List<String> validateFhirConnectModel(final FhirConnectModel modelMapper) {
        final List<String> errors = new ArrayList<>();
//        if (!modelMapper.getFormat().equals("0.0.2")) {
//            errors.add("Format of the model modelMapper needs to be 0.0.2");
//        }

        // todo: imeplement other validation (fhir path, openehr paths, ..)

        validateSlotContexts(modelMapper.getMappings(), errors);

        return errors;
    }

    private void validateSlotContexts(final List<Mapping> mappings, final List<String> errors) {
        if (mappings == null) {
            return;
        }
        for (final Mapping mapping : mappings) {
            if (mapping.getSlotContext() != null) {
                if (mapping.getSlotArchetype() != null) {
                    errors.add(String.format("Mapping '%s': slotArchetype and slotContext are mutually exclusive.",
                                             mapping.getName()));
                }
                if (mapping.getWith() != null && mapping.getWith().getOpenehr() != null
                        && mapping.getWith().getOpenehr().startsWith(FhirConnectConst.REFERENCE)) {
                    errors.add(String.format(
                            "Mapping '%s': slotContext with openehr: $reference would create a separate composition, which is not supported.",
                            mapping.getName()));
                }
            }
            if (mapping.getFollowedBy() != null) {
                validateSlotContexts(mapping.getFollowedBy().getMappings(), errors);
            }
            if (mapping.getReference() != null) {
                validateSlotContexts(mapping.getReference().getMappings(), errors);
            }
        }
    }

    /**
     * Cross-file sub-context checks of a context mapper.
     *
     * @param context context being validated
     * @param tenantContexts the other contexts known so far, used to follow 'contexts' references
     * @param models the models (archetypes and extensions) of the context that are known so far
     * @return 'undeclared sub-context' for every slotContext of the given models that the context doesn't list
     *         under 'contexts', and 'cyclic context reference' if following 'contexts' leads back to the context
     */
    public List<String> validateSubContexts(final FhirConnectContext context,
                                            final List<FhirConnectContext> tenantContexts,
                                            final List<FhirConnectModel> models) {
        final List<String> errors = new ArrayList<>();
        final List<String> declared = context.getContext().getContexts() == null
                ? List.of() : context.getContext().getContexts();

        final Set<String> usedSlotContexts = new LinkedHashSet<>();
        for (final FhirConnectModel model : models) {
            collectSlotContexts(model.getMappings(), usedSlotContexts);
        }
        usedSlotContexts.stream()
                .filter(used -> !declared.contains(used))
                .forEach(used -> errors.add(
                        String.format("Undeclared sub-context: slotContext '%s' is not listed under contexts.", used)));

        final String name = context.getMetadata() == null ? null : context.getMetadata().getName();
        final Map<String, FhirConnectContext> byName = new HashMap<>();
        for (final FhirConnectContext tenantContext : tenantContexts) {
            if (tenantContext.getMetadata() != null && tenantContext.getMetadata().getName() != null) {
                byName.put(tenantContext.getMetadata().getName(), tenantContext);
            }
        }
        if (name != null) {
            byName.put(name, context); // the one being validated takes precedence over its stored version
            final String cycle = findCycle(name, byName, new ArrayList<>());
            if (cycle != null) {
                errors.add("Cyclic context reference: " + cycle);
            }
        }
        return errors;
    }

    private void collectSlotContexts(final List<Mapping> mappings, final Set<String> slotContexts) {
        if (mappings == null) {
            return;
        }
        for (final Mapping mapping : mappings) {
            if (mapping.getSlotContext() != null) {
                slotContexts.add(mapping.getSlotContext());
            }
            if (mapping.getFollowedBy() != null) {
                collectSlotContexts(mapping.getFollowedBy().getMappings(), slotContexts);
            }
            if (mapping.getReference() != null) {
                collectSlotContexts(mapping.getReference().getMappings(), slotContexts);
            }
        }
    }

    private String findCycle(final String name, final Map<String, FhirConnectContext> byName,
                             final List<String> path) {
        if (path.contains(name)) {
            return String.join(" -> ", path.subList(path.indexOf(name), path.size())) + " -> " + name;
        }
        final FhirConnectContext context = byName.get(name);
        if (context == null || context.getContext() == null || context.getContext().getContexts() == null) {
            return null;
        }
        path.add(name);
        for (final String next : context.getContext().getContexts()) {
            final String cycle = findCycle(next, byName, path);
            if (cycle != null) {
                return cycle;
            }
        }
        path.remove(path.size() - 1);
        return null;
    }

}
