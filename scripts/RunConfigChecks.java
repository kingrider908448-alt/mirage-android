import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;

/** Run from the repository root: java scripts/RunConfigChecks.java (JDK 17+). */
class RunConfigChecks {
    public static void main(String[] args) throws Exception {
        Path output = Files.createTempDirectory("ghostviki-config-checks-");
        List<String> compilerArgs = new ArrayList<>(List.of("-d", output.toString(), "-Xlint:unchecked"));
        for (String directory : List.of("core/src/main/java", "core/src/test/java", "scripts/config-checks")) {
            try (var sources = Files.walk(Path.of(directory))) {
                sources.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> compilerArgs.add(p.toString()));
            }
        }
        compilerArgs.add("app/src/main/java/dev/ghostviki/app/ConfigStore.java");
        compilerArgs.add("app/src/main/java/dev/ghostviki/app/hooks/HookConfig.java");
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("A full JDK is required");
        if (compiler.run(null, System.out, System.err, compilerArgs.toArray(String[]::new)) != 0)
            throw new AssertionError("Configuration checks did not compile");
        // Parse UI and module sources too. Android/LSPosed type checking is still done by Gradle/CI.
        List<Path> androidSources = new ArrayList<>();
        for (String directory : List.of("app/src/main/java", "probe/src/main/java")) {
            try (var sources = Files.walk(Path.of(directory))) {
                sources.filter(p -> p.toString().endsWith(".java")).forEach(androidSources::add);
            }
        }
        try (var manager = compiler.getStandardFileManager(null, null, null)) {
            var diagnostics = new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();
            var task = (com.sun.source.util.JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none"), null, manager.getJavaFileObjectsFromPaths(androidSources));
            task.parse();
            if (diagnostics.getDiagnostics().stream().anyMatch(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR))
                throw new AssertionError(diagnostics.getDiagnostics().toString());
            System.out.println("PASS: parsed all Android Java sources (syntax only)");
        }
        try (var loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            for (String main : List.of("dev.ghostviki.core.CoreChecks", "dev.ghostviki.app.hooks.ConfigChecks"))
                loader.loadClass(main).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        }
    }
}
