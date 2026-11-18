package com.pocket;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.pocket", importOptions = ImportOption.DoNotIncludeTests.class)
class ReglasDeCapasTest {

    private static final String BASE = "com.pocket";

    @ArchTest
    static final ArchRule elControllerSoloHablaConServices = noClasses()
            .that().resideInAPackage(BASE + ".controller..")
            .should().dependOnClassesThat().resideInAnyPackage(BASE + ".repository..", BASE + ".domain..", BASE + ".mapper..")
            .because("el controller sólo recibe, delega en un service y devuelve: "
                    + "si toca entidades o repositorios, la lógica de negocio se escapa a la capa HTTP")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule elDominioNoConoceLasCapasDeArriba = noClasses()
            .that().resideInAPackage(BASE + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(BASE + ".dto..", BASE + ".service..", BASE + ".mapper..", BASE + ".controller..")
            .because("las entidades no saben cómo se exponen; esa traducción es del mapper")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule elRepositorioNoTieneLogica = noClasses()
            .that().resideInAPackage(BASE + ".repository..")
            .should().dependOnClassesThat().resideInAnyPackage(BASE + ".dto..", BASE + ".service..", BASE + ".controller..")
            .because("los repositorios son interfaces de Spring Data sobre entidades, sin lógica ni DTOs")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule elServiceNoDependeDelController = noClasses()
            .that().resideInAPackage(BASE + ".service..")
            .should().dependOnClassesThat().resideInAPackage(BASE + ".controller..")
            .because("las dependencias van de HTTP hacia el negocio, nunca al revés")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule loPublicoNoDependeDeLaAdministracion = noClasses()
            .that().resideInAPackage(BASE + ".service.publico..")
            .should().dependOnClassesThat().resideInAPackage(BASE + ".service.administracion..")
            .because("el sitio público tiene que poder leer sin arrastrar lo que sólo usa el panel")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule sinCiclosEntreCapas = slices()
            .matching(BASE + ".(*)..")
            .should().beFreeOfCycles()
            .because("un ciclo entre capas deja de tener un arriba y un abajo, y cualquier cambio arrastra a las dos")
            .allowEmptyShould(true);
}
