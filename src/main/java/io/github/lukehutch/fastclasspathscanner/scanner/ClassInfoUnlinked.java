package io.github.lukehutch.fastclasspathscanner.scanner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import io.github.lukehutch.fastclasspathscanner.utils.Join;
import io.github.lukehutch.fastclasspathscanner.utils.LoggedThread.ThreadLog;

/**
 * Class information that has been directly read from the binary classfile, before it is cross-linked with other
 * classes. (The cross-linking is done in a separate step to avoid the complexity of dealing with race conditions.)
 */
class ClassInfoUnlinked {
    String className;
    private boolean isInterface;
    private boolean isAnnotation;
    // Superclass (can be null if no superclass, or if superclass is blacklisted)
    private String superclassName;
    private List<String> implementedInterfaces;
    private List<String> annotations;
    private Set<String> fieldTypes;
    private Map<String, Object> staticFinalFieldValues;
    private ConcurrentHashMap<String, String> stringInternMap;

    ClassInfoUnlinked() {
    }

    private String intern(final String string) {
        if (string == null) {
            return null;
        }
        final String oldValue = stringInternMap.putIfAbsent(string, string);
        return oldValue == null ? string : oldValue;
    }

    ClassInfoUnlinked(final String className, final boolean isInterface, final boolean isAnnotation,
            final ConcurrentHashMap<String, String> stringInternMap) {
        this.stringInternMap = stringInternMap;
        this.className = intern(className);
        this.isInterface = isInterface;
        this.isAnnotation = isAnnotation;
    }

    void addSuperclass(final String superclassName) {
        this.superclassName = intern(superclassName);
    }

    void addImplementedInterface(final String interfaceName) {
        if (implementedInterfaces == null) {
            implementedInterfaces = new ArrayList<>();
        }
        implementedInterfaces.add(intern(interfaceName));
    }

    void addAnnotation(final String annotationName) {
        if (annotations == null) {
            annotations = new ArrayList<>();
        }
        annotations.add(intern(annotationName));
    }

    void addFieldType(final String fieldTypeName) {
        if (fieldTypes == null) {
            fieldTypes = new HashSet<>();
        }
        fieldTypes.add(intern(fieldTypeName));
    }

    void addFieldConstantValue(final String fieldName, final Object staticFinalFieldValue) {
        if (staticFinalFieldValues == null) {
            staticFinalFieldValues = new HashMap<>();
        }
        staticFinalFieldValues.put(intern(fieldName), staticFinalFieldValue);
    }

    void link(final Map<String, ClassInfo> classNameToClassInfo) {
        final ClassInfo classInfo = ClassInfo.addScannedClass(className, isInterface, isAnnotation,
                classNameToClassInfo);
        if (superclassName != null) {
            classInfo.addSuperclass(superclassName, classNameToClassInfo);
        }
        if (implementedInterfaces != null) {
            for (final String interfaceName : implementedInterfaces) {
                classInfo.addImplementedInterface(interfaceName, classNameToClassInfo);
            }
        }
        if (annotations != null) {
            for (final String annotationName : annotations) {
                classInfo.addAnnotation(annotationName, classNameToClassInfo);
            }
        }
        if (fieldTypes != null) {
            for (final String fieldTypeName : fieldTypes) {
                classInfo.addFieldType(fieldTypeName, classNameToClassInfo);
            }
        }
        if (staticFinalFieldValues != null) {
            for (final Entry<String, Object> ent : staticFinalFieldValues.entrySet()) {
                classInfo.addFieldConstantValue(ent.getKey(), ent.getValue());
            }
        }
    }

    void logTo(final ThreadLog log) {
        if (FastClasspathScanner.verbose) {
            log.log(2, "Found " + (isAnnotation ? "annotation class" : isInterface ? "interface class" : "class")
                    + " " + className);
            if (superclassName != null && !"java.lang.Object".equals(superclassName)) {
                log.log(3,
                        "Super" + (isInterface && !isAnnotation ? "interface" : "class") + ": " + superclassName);
            }
            if (implementedInterfaces != null) {
                log.log(3, "Interfaces: " + Join.join(", ", implementedInterfaces));
            }
            if (annotations != null) {
                log.log(3, "Annotations: " + Join.join(", ", annotations));
            }
            if (fieldTypes != null) {
                log.log(3, "Field types: " + Join.join(", ", fieldTypes));
            }
            if (staticFinalFieldValues != null) {
                final List<String> fieldInitializers = new ArrayList<>();
                for (final Entry<String, Object> ent : staticFinalFieldValues.entrySet()) {
                    fieldInitializers.add(ent.getKey() + " = " + ent.getValue());
                }
                log.log(3, "Static final field values: " + Join.join(", ", fieldInitializers));
            }
        }
    }
}