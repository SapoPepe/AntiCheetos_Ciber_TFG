import moss.MossInvoker;
import DomjudgeDownloader.DomjudgeDownloader;

import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;


public class Main {
    public static void main(String[] args) {
        // Moss configuration
        MossInvoker moss = new MossInvoker();
        Set<String> mossLanguages = Set.of("c", "c++", "c#", "java", "python", "mips assembly", "a8086 assembly");

        Scanner scanner = new Scanner(System.in);

        boolean exit = false;

        while (!exit) {
            System.out.print("1. Domjudge file analysis\n2. Nothing\n3. Exit\nInsert option: ");
            int option = scanner.nextInt();
            scanner.nextLine();
            switch (option) {
                case 1:
                    System.out.print("Base URL (ej: https://yourhost/domjudge): ");
                    String baseApi = scanner.nextLine();
                    System.out.print("Contest ID (cid): ");
                    String cid = scanner.nextLine();
                    System.out.print("User: ");
                    String user = scanner.nextLine();
                    System.out.print("Password: ");
                    String pass = scanner.nextLine();

                    DomjudgeDownloader domjudge = new DomjudgeDownloader(baseApi, user, pass);

                    Path downloads;
                    try {
                        downloads = domjudge.downloader(cid);
                    } catch (Exception e){
                        System.err.println("Error while domjudgeDownloader working\n" + e.getMessage());
                        break;
                    }

                    // Find the extension/language of problem files
                    List<String> extensions = moss.findProblemLanguage(downloads);

                    // For each problem we generate a petition to moss
                    try(Stream<Path> problems = Files.list(downloads)){
                        problems.filter(Files::isDirectory)
                                .forEach(problem -> {
                                    List<String> files;
                                    try {
                                        files = Files.list(problem)
                                                .filter(Files::isRegularFile)
                                                .map(path -> path.toAbsolutePath().toString())
                                                .toList();

                                        moss.run(extensions.get(0), files);
                                        extensions.remove(0);
                                        System.out.println("Submissions result of " + problem.toString() + " problem");

                                    } catch (Exception e){
                                        System.err.println("Files from " + problem + " couldn't be read");
                                    }
                                });
                    } catch (Exception e){
                        System.err.println("Directory ./downloads can't be read");
                    }






                    /*
                    System.out.print("Insert path to directory: ");
                    Path dirPath = Paths.get(scanner.nextLine());
                    if (Files.isDirectory(dirPath)) {
                        // Check if dir is empty
                        boolean full;
                        try {
                            DirectoryStream<Path> dirStream = Files.newDirectoryStream(dirPath);
                            full = dirStream.iterator().hasNext();
                        } catch (Exception e) {
                            full = false;
                        }

                        if (full) {
                            System.out.print("Insert language: ");
                            String lan = scanner.nextLine().toLowerCase();
                            if (mossLanguages.contains(lan)) {
                                List<String> files;
                                try {
                                    files = Files.list(dirPath)
                                            .filter(Files::isRegularFile)
                                            .map(path -> path.toAbsolutePath().toString())
                                            .toList();
                                } catch (Exception e){
                                    files = new ArrayList<>();
                                }

                                moss.run(lan, files);
                            } else System.err.println("Language not supported");
                        } else System.err.println("Empty directory");
                    } else System.err.println("Is not a directory: " + dirPath);
                    */

                    break;

                case 2:

                case 4:
                    System.out.print("Bye");
                    exit = true;
                    break;

                default:
                    System.out.println("Not valid option");
            }
        }
    }

}