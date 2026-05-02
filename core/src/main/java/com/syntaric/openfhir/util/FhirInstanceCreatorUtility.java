package com.syntaric.openfhir.util;

import ca.uhn.fhir.model.api.IElement;
import ca.uhn.fhir.model.api.annotation.Child;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.instance.model.api.IBaseEnumeration;
import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IDomainResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.syntaric.openfhir.util.OpenFhirStringUtils.RESOLVE;
import static com.syntaric.openfhir.util.OpenFhirStringUtils.WHERE;

/**
 * Creates and instantiates HAPI FHIR Resources based on FHIR Path expressions
 */
@Slf4j
@Component
public class FhirInstanceCreatorUtility {

    private final OpenFhirStringUtils openFhirStringUtils;

    @Autowired
    public FhirInstanceCreatorUtility(OpenFhirStringUtils openFhirStringUtils) {
        this.openFhirStringUtils = openFhirStringUtils;
    }


    /**
     * Creates a new Resource of the resourceType
     *
     * @param resourceType type of a FHIR Resource. If this is not a valid Resource type, exception will be
     *                     logged
     *                     and null will be returned
     * @return instantiated FHIR Resource
     */
    public IAnyResource create(final String resourceType,
                               final String modelPackage) {
        try {
            return getFhirResourceType(resourceType, modelPackage).getDeclaredConstructor().newInstance();
        } catch (final Exception e) {
            log.error("Couldn't create a new instance of {}", resourceType, e);
            return null;
        }
    }

    public String getWhereForInstantiation(final String fhirPath,
                                           final Class clazz) {
        final String[] splitByDot = fhirPath.split("\\.");
        if (splitByDot.length > 1) {
            boolean shouldHandleWhere;
            if (splitByDot[0].equals(clazz.getSimpleName()) && splitByDot.length > 2) {
                shouldHandleWhere = splitByDot[1].startsWith(WHERE) || splitByDot[2].startsWith(WHERE);
            } else {
                shouldHandleWhere = splitByDot[1].startsWith(WHERE);
            }

            if (fhirPath.startsWith(WHERE)) {
                shouldHandleWhere = true;
            }

            if (shouldHandleWhere) {
                return openFhirStringUtils.extractWhereCondition(fhirPath);
            }
        }
        return null;
    }

    /**
     * Creates a list from split paths, removing resolve elements and cast elements if what follows
     * is resolving or casting
     *
     * @param splitFhirPaths all split paths up unitl now
     * @param resolveFollows whether resolve follows next
     * @param castFollows    whether cast follows next
     * @return a List of all split elements without cast or resolve segments (as(), resolve())
     */
    public List<String> listFromSplitPath(final String[] splitFhirPaths,
                                          final boolean resolveFollows,
                                          final boolean castFollows) {
        return Arrays.stream(splitFhirPaths)
                .filter(en -> {
                    if (resolveFollows) {
                        return StringUtils.isNotEmpty(en) && !en.equals(RESOLVE);
                    } else if (castFollows) {
                        return StringUtils.isNotEmpty(en) && !en.startsWith("as(");
                    } else {
                        return StringUtils.isNotEmpty(en);
                    }
                })
                .collect(Collectors.toList());
    }

    public Object handleSet(final Object generatedInstance, final boolean resolveFollows,
                            final Field theField,
                            final Object resource,
                            final String modelPackage) {
        final boolean isReference = generatedInstance instanceof IDomainResource && !resolveFollows;
        Object objectToReturn = isReference ? newReference(modelPackage) : generatedInstance;
        final boolean isEnumeration =
                IBaseEnumeration.class.isAssignableFrom(theField.getType()) && !(objectToReturn instanceof IBaseEnumeration);
        objectToReturn = isEnumeration ? newEnumeration(modelPackage) : generatedInstance;
        return setFieldObject(theField, resource, objectToReturn, modelPackage);
    }

    private IBaseReference newReference(final String modelPackage) {
        return (IBaseReference) newInstance(getClassForName(modelPackage + "Reference"));
    }

    private IBaseEnumeration<?> newEnumeration(final String modelPackage) {
        return (IBaseEnumeration<?>) newInstance(getClassForName(modelPackage + "Enumeration"));
    }

    public Object setFieldObject(final Field theField, final Object resource, final Object settingObject,
                                 final String modelPackage) {
        if (theField == null) {
            return null;
        }
        final Object value = wrapInReferenceIfNeeded(settingObject, modelPackage);
        try {
            theField.setAccessible(true);
            if (theField.getType() == List.class) {
                final List<Object> list = new ArrayList<>();
                if (theField.get(resource) == null) {
                    theField.set(resource, list);
                    list.add(value);
                    return list;
                } else {
                    final List<Object> existingList = (List<Object>) theField.get(resource);
                    existingList.add(value);
                    return existingList;
                }
            } else {
                if ("BundleEntryComponent".equals(theField.getDeclaringClass().getSimpleName())
                        && value instanceof IBaseReference refValue) {
                    setBundleEntryResource(resource, (IBaseResource) refValue.getResource());
                } else {
                    theField.set(resource, value);
                }
            }
        } catch (IllegalAccessException e) {
            log.error("Error trying to set field object.", e);
        }
        return value;
    }

    private void setBundleEntryResource(final Object bundleEntry, final IBaseResource resource) {
        if (bundleEntry instanceof org.hl7.fhir.r4.model.Bundle.BundleEntryComponent r4Entry) {
            r4Entry.setResource((org.hl7.fhir.r4.model.Resource) resource);
        } else if (bundleEntry instanceof org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent stu3Entry) {
            stu3Entry.setResource((org.hl7.fhir.dstu3.model.Resource) resource);
        } else if (bundleEntry instanceof org.hl7.fhir.r4b.model.Bundle.BundleEntryComponent r4bEntry) {
            r4bEntry.setResource((org.hl7.fhir.r4b.model.Resource) resource);
        } else if (bundleEntry instanceof org.hl7.fhir.r5.model.Bundle.BundleEntryComponent r5Entry) {
            r5Entry.setResource((org.hl7.fhir.r5.model.Resource) resource);
        } else {
            log.error("Unsupported BundleEntryComponent type: {}", bundleEntry.getClass().getName());
        }
    }

    public Object wrapInReferenceIfNeeded(final Object settingObject, final String modelPackage) {
        if (settingObject instanceof IDomainResource domainResource) {
            final IBaseReference ref = newReference(modelPackage);
            ref.setResource(domainResource);
            return ref;
        }
        return settingObject;
    }

    public Class findClass(final Field field, final String forcingClass, final String modelPackage) {
        if (field == null) {
            return null;
        }
        final Child childAnnotation = field.getAnnotation(Child.class);

        final Class<? extends IElement>[] types = childAnnotation.type();

        if (types.length == 0) {
            // backboneelement
            if (field.getGenericType() instanceof ParameterizedType) {
                final Type actualTypeArgument = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                return getClassForName(actualTypeArgument.getTypeName());
            }
            if (StringUtils.isNotEmpty(forcingClass) && field.getType().getName().endsWith("Type")) {
                return getClassForName(field.getType().getName().replace("Type", forcingClass));
            } else {
                return getClassForName(field.getType().getName());
            }
        } else {
            if (forcingClass == null) {
                if (IBaseEnumeration.class.isAssignableFrom(field.getType())) {
                    return field.getType();
                }
                return types[0];
            } else if (field.getGenericType() instanceof ParameterizedType) {
                final Type actualTypeArgument = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                if (actualTypeArgument.getTypeName().contains("Enumeration")) {
                    return getClassForName(modelPackage + "Enumeration");
                }
            } else if ("extension".equals(field.getName()) || "modifierExtension".equals(field.getName())) {
                // special handling: resolve Extension from the correct model package
                return getClassForName(modelPackage + "Extension");
            }
            return findByForcingClass(types, forcingClass, modelPackage);
        }
    }

    private Class findByForcingClass(final Class<? extends IElement>[] types,
                                     final String forcingClass,
                                     final String modelPackage) {
        return Arrays.stream(types)
                .filter(type -> {
                    final Class classForName = getClassForName(type.getName());
                    if (classForName == null) {
                        return false;
                    }
                    return forcingClass.equals(type.getSimpleName())
                            || ("Coding".equals(forcingClass) && "CodeType".equals(type.getSimpleName()))
                            || getFhirResourceType(forcingClass, modelPackage).isAssignableFrom(classForName);
                })
                .map(type -> getClassForName(
                        type.getName())) // if getClassForName was false, then element wouldn't be in the list as its filtered by that above
                .findFirst()
                .orElse(null);
    }

    public Object newInstance(final Class clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (final Exception e) {
            log.error("Error trying to create a new instance of class: {}", clazz, e);
        }
        return null;
    }

    public Class getClassForName(final String name) {
        try {
            return Class.forName(name);
        } catch (final Exception e) {
            log.error("Error: ", e);
            return null;
        }
    }

    public String prepareFhirPathForInstantiation(final Class clazz, final String fhirPath) {
        String prepared = fhirPath;

        if (fhirPath.startsWith(clazz.getSimpleName())) {
            prepared = prepared.replace(clazz.getSimpleName() + ".", "");
        }

        return prepared;
    }

    public Class<? extends IAnyResource> getFhirResourceType(final String resourceName, final String modelPackage) {
        try {
            return (Class<? extends IAnyResource>) Class.forName(modelPackage + resourceName);
        } catch (final ClassNotFoundException e) {
            return null;
        }
    }
}
