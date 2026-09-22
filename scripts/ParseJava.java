import com.sun.source.util.JavacTask;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

/** Parse Java source without claiming to resolve Android APIs or dependencies. */
class ParseJava {
    public static void main(String[] args) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, null);
             var paths = Files.walk(Path.of(args.length == 0 ? "." : args[0]))) {
            List<Path> sources = paths.filter(p -> p.toString().endsWith(".java")
                    && p.toString().contains("src/")).toList();
            var units = manager.getJavaFileObjectsFromPaths(sources);
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "17", "-proc:none"), null, units);
            task.parse();
            boolean errors = false;
            for (var diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) { System.err.println(diagnostic); errors = true; }
            }
            if (errors) System.exit(1);
            System.out.println("PASS: parsed " + sources.size() + " Java source files (syntax only; not an Android build)");
        }
    }
}
