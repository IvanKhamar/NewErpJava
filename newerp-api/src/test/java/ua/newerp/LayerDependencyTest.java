package ua.newerp;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Dependencies point inward only: Api -> Infrastructure -> Application -> Domain; Api -> Web -> Application. */
@AnalyzeClasses(packages = "ua.newerp", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerDependencyTest {

    @ArchTest
    static final ArchRule layersPointInward = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("ua.newerp.domain..")
            .layer("Application").definedBy("ua.newerp.application..")
            .layer("Infrastructure").definedBy("ua.newerp.infrastructure..")
            .layer("Web").definedBy("ua.newerp.web..")
            .layer("Api").definedBy("ua.newerp.api..")
            .whereLayer("Api").mayNotBeAccessedByAnyLayer()
            .whereLayer("Web").mayOnlyBeAccessedByLayers("Api")
            .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Api")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure", "Web", "Api")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Web", "Api");

    // The domain is plain Java: no Spring, no JPA annotations, no database types.
    @ArchTest
    static final ArchRule domainIsPlainJava = classes()
            .that().resideInAPackage("ua.newerp.domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("ua.newerp.domain..", "java..");
}
