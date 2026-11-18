package com.pocket;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;

@AnalyzeClasses(packages = "com.pocket", importOptions = ImportOption.DoNotIncludeTests.class)
class ReglasDeNombresTest {

    private static final String BASE = "com.pocket";

    @ArchTest
    static final ArchRule controllers = classes()
            .that().resideInAPackage(BASE + ".controller..").and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("Controller")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule repositorios = classes()
            .that().resideInAPackage(BASE + ".repository..").and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("Repository")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule interfacesDeMapper = classes()
            .that().resideInAPackage(BASE + ".mapper").and().areTopLevelClasses()
            .should().beInterfaces().andShould().haveSimpleNameEndingWith("Mapper")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule implementacionesDeMapper = classes()
            .that().resideInAPackage(BASE + ".mapper.impl..").and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("MapperImpl")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule excepciones = classes()
            .that().resideInAPackage(BASE + "..").and().areAssignableTo(Throwable.class)
            .should().haveSimpleNameEndingWith("Exception")
            .andShould().resideInAPackage(BASE + ".exception..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule entidades = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage(BASE + ".domain..")
            .allowEmptyShould(true);
}
