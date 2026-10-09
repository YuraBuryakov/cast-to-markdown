package io.github.yuraburyakov.casttomarkdown.html;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ResolvedModule;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * An application on the module path that requires only the core module must get everything HTML needs.
 * Surefire runs the tests with {@code --add-modules ALL-MODULE-PATH}, which hides a missing module,
 * so the module graph is resolved here again from the module path, the way {@code java -m app} does.
 */
class ModulePathTest {

    @Test
    void htmlModuleBringsItsWholeModuleGraph() {
        String modulePath = System.getProperty("jdk.module.path");
        assertThat(modulePath).as("tests run on the module path").isNotNull();
        ModuleFinder finder = ModuleFinder.of(Arrays.stream(modulePath.split(File.pathSeparator))
                .map(Path::of).toArray(Path[]::new));
        Configuration system = Configuration.empty().resolve(ModuleFinder.ofSystem(), ModuleFinder.of(), Set.of("java.se"));

        Configuration application = Configuration.resolveAndBind(finder, List.of(system), ModuleFinder.of(),
                Set.of("io.github.yuraburyakov.casttomarkdown"));

        Set<String> modules = application.modules().stream().map(ResolvedModule::name).collect(Collectors.toSet());
        // the HTML module comes in through the service binding of the core module
        assertThat(modules).contains("io.github.yuraburyakov.casttomarkdown.html", "org.jsoup");
    }
}
