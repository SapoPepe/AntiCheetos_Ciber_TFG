package moss;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class MossInvoker {
    private static final String MOSS_PATH = "moss.pl";      // Change if needed

    public void run(String lan, List<String> files){

        // Check moss
        File moss = new File(MOSS_PATH);
        if(!moss.exists()){
            System.err.println("Can't find moss script in path " + MOSS_PATH);
            return;
        }

        // Command construction
        List<String> command = new ArrayList<>();
        command.addAll(List.of("perl", MOSS_PATH, "-l", lan));
        command.addAll(files);

        // Process builder
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.inheritIO();        // Redirects output and error to java console
        try {
            Process p = builder.start();
            int exit = p.waitFor();

            if(exit != 0) System.err.println("Finished with errors. Code: " + exit);
        } catch (Exception e){
            System.err.println("An error has occurred while executing: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    public List<String> findProblemLanguage(Path downloads){
        List<String> languages = new ArrayList<>();

        try (Stream<Path> problems = Files.list(downloads)){
            problems.filter(Files::isDirectory)
                    .forEach(problem -> {
                        try (Stream<Path> files = Files.list(problem)){
                            Optional<Path> firstFile = files.filter(Files::isRegularFile)
                                    .findFirst();

                            firstFile.ifPresent(file -> {
                                String extension = FilenameUtils.getExtension(file.getFileName().toString());
                                languages.add(mapExtensionToMoss(extension));
                            });

                        } catch (Exception e){
                            System.err.println("Files from " + problem + " couldn't be read");
                        }
                    });
            /*
            Path problem = problems.filter(Files::isDirectory)
                    .findFirst()
                    .orElseThrow(() -> new Exception("No problems to check"));

            try (Stream<Path> files = Files.list(problem)) {
                Path firstFile = files.filter(Files::isRegularFile)
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("No files in " + problem));

                String extension = FilenameUtils.getExtension(firstFile.getFileName().toString());
                System.out.println("Primer archivo: " + firstFile.getFileName() + " (ext: " + extension + ")");

                languages.add(mapExtensionToMoss(extension));
            }*/
        } catch (Exception e) {
            System.err.println("Directory ./downloads can't be read");
        }
        return languages;
    }

    private String mapExtensionToMoss(String extension){
        return switch (extension.toLowerCase()) {
            case "c" -> "c";
            case "cpp" -> "cc";
            case "cs" -> "csharp";
            case "java" -> "java";
            case "s", "asm" -> "assembly";
            case "py" -> "python";
            default -> null;
        };
    }
}
