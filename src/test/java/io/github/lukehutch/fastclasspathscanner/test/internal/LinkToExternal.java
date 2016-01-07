package io.github.lukehutch.fastclasspathscanner.test.internal;

import static org.assertj.core.api.Assertions.assertThat;
import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import io.github.lukehutch.fastclasspathscanner.test.external.ExternalAnnotation;
import io.github.lukehutch.fastclasspathscanner.test.external.ExternalInterface;
import io.github.lukehutch.fastclasspathscanner.test.external.ExternalSuperclass;

import org.junit.Test;

public class LinkToExternal {

    public static abstract class ExtendsExternal extends ExternalSuperclass {
    }

    public static abstract class ImplementsExternal implements ExternalInterface {
    }

    @ExternalAnnotation
    public static abstract class AnnotatedByExternal {
    }

    @Test
    public void testWhitelistingExternalClasses() {
        FastClasspathScanner scanner = new FastClasspathScanner(LinkToExternal.class.getPackage().getName(),
                ExternalSuperclass.class.getPackage().getName(), ExternalInterface.class.getName(),
                AnnotatedByExternal.class.getName()).scan();
        assertThat(scanner.getNamesOfAllStandardClasses()).containsOnly(LinkToExternal.class.getName(),
                ExtendsExternal.class.getName(), ImplementsExternal.class.getName(),
                AnnotatedByExternal.class.getName(), ExternalSuperclass.class.getName());
        assertThat(scanner.getNamesOfSubclassesOf(ExternalSuperclass.class.getName())).containsExactly(
                ExtendsExternal.class.getName());
        assertThat(scanner.getNamesOfAllInterfaceClasses()).containsExactly(ExternalInterface.class.getName());
        assertThat(scanner.getNamesOfClassesImplementing(ExternalInterface.class.getName())).containsExactly(
                ImplementsExternal.class.getName());
        assertThat(scanner.getNamesOfAllAnnotationClasses()).containsExactly(ExternalAnnotation.class.getName());
        assertThat(scanner.getNamesOfClassesWithAnnotation(ExternalAnnotation.class.getName())).containsExactly(
                AnnotatedByExternal.class.getName());
    }

    @Test
    public void testIncludeReferencedClasses() {
        FastClasspathScanner scanner = new FastClasspathScanner(LinkToExternal.class.getPackage().getName()).scan();
        assertThat(scanner.getNamesOfAllStandardClasses()).doesNotContain(ExternalSuperclass.class.getName());
        assertThat(scanner.getNamesOfSubclassesOf(ExternalSuperclass.class.getName())).containsExactly(
                ExtendsExternal.class.getName());
        assertThat(scanner.getNamesOfAllInterfaceClasses()).doesNotContain(ExternalInterface.class.getName());
        assertThat(scanner.getNamesOfClassesImplementing(ExternalInterface.class.getName())).containsExactly(
                ImplementsExternal.class.getName());
        assertThat(scanner.getNamesOfAllAnnotationClasses()).doesNotContain(ExternalAnnotation.class.getName());
        assertThat(scanner.getNamesOfClassesWithAnnotation(ExternalAnnotation.class.getName())).containsExactly(
                AnnotatedByExternal.class.getName());
    }
}
