package com.syntaric.openfhir.mapping.custommappings;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CustomMappingRegistry {

    private static final String BASE_PACKAGE = "com.syntaric.openfhir.mapping.custommappings";

    private final ApplicationContext applicationContext;
    private final Map<String, CustomMapping> byCode = new HashMap<>();

    @Autowired
    public CustomMappingRegistry(final ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        loadMappings();
    }

    public CustomMappingRegistry() {
        this.applicationContext = null;
        loadMappings();
    }

    public Optional<CustomMapping> find(final String mappingCode) {
        if (mappingCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byCode.get(mappingCode));
    }

    public Collection<CustomMapping> all() {
        return byCode.values();
    }

    private void loadMappings() {
        final ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(CustomMapping.class));

        scanner.findCandidateComponents(BASE_PACKAGE).forEach(beanDef -> {
            try {
                Class<?> cls = Class.forName(beanDef.getBeanClassName());
                if (!CustomMapping.class.isAssignableFrom(cls)) {
                    return;
                }
                @SuppressWarnings("unchecked")
                Class<? extends CustomMapping> mappingClass = (Class<? extends CustomMapping>) cls;
                if (java.lang.reflect.Modifier.isAbstract(mappingClass.getModifiers())) {
                    return;
                }
                CustomMapping mapping = resolveInstance(mappingClass);
                register(mapping);
            } catch (Exception e) {
                log.warn("Failed loading custom mapping {}: {}", beanDef.getBeanClassName(), e.getMessage());
            }
        });
    }

    private CustomMapping resolveInstance(final Class<? extends CustomMapping> mappingClass) {
        if (applicationContext != null) {
            try {
                return applicationContext.getBean(mappingClass);
            } catch (BeansException ignored) {
                // fall through to reflection
            }
        }
        try {
            return mappingClass.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not instantiate custom mapping " + mappingClass.getName(), ex);
        }
    }

    private void register(final CustomMapping mapping) {
        if (mapping == null) {
            return;
        }
        Set<String> codes = mapping.mappingCodes();
        if (codes == null || codes.isEmpty()) {
            log.warn("Custom mapping {} provides no mapping codes", mapping.getClass().getName());
            return;
        }
        for (String code : codes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            if (byCode.containsKey(code)) {
                log.warn("Duplicate custom mapping code {}. Keeping {}", code, byCode.get(code).getClass().getName());
                continue;
            }
            byCode.put(code, mapping);
        }
    }
}
