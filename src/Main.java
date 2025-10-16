import ctfd.CheckFlags;
import ctfd.FlagCopied;
import imageAnalyzer.ImageAnalizer;
import imageAnalyzer.Match;
import moss.MossInvoker;
import domjudgeDownloader.DomjudgeDownloader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;


public class Main {
    public static void main(String[] args) {
        // Moss configuration
        MossInvoker moss = new MossInvoker();

        Scanner scanner = new Scanner(System.in);

        boolean exit = false;

        while (!exit) {
            System.out.println("----------------------------------------");
            System.out.print("1. Domjudge file analysis\n2. Image file analysis\n3. Check flags\n4. Exit\nInsert option: ");
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

                                        System.out.println("-----------------------------------------------------------------------------");
                                        System.out.println("Submissions result of " + problem + " problem");
                                        moss.run(extensions.get(0), files);
                                        extensions.remove(0);

                                    } catch (Exception e){
                                        System.err.println("Files from " + problem + " couldn't be read");
                                    }
                                });
                    } catch (Exception e){
                        System.err.println("Directory ./downloads can't be read");
                    }
                    break;

                case 2:
                    ImageAnalizer ia = new ImageAnalizer();
                    try{
                        List<Match> matches = ia.analyzer();
                        System.out.println("--------------------------------\n> RESULTS\n--------------------------------");
                        for (var m : matches) {
                            System.out.println(m);
                        }
                        System.out.println("> End of results");
                    } catch (Exception e){
                        System.err.println("[ERROR] Could not execute image analyzer");
                    }

                    break;

                case 3:
                    System.out.print("Base URL: ");
                    String url = scanner.nextLine();
                    System.out.print("CTFd API_KEY: ");
                    String api_key = scanner.nextLine();

                    CheckFlags checker = new CheckFlags();
                    try {
                        List<FlagCopied> copies = checker.checkFlags(api_key, url);
                        System.out.println("--------------------------------\n> RESULTS\n--------------------------------");
                        if(copies.isEmpty()) System.out.println("There is not flag copied");
                        for(FlagCopied copy : copies) System.out.println(copy);
                        System.out.println("> End of results");
                    } catch (Exception e) {
                        System.err.println("[ERROR] In CTFd checker");
                        System.err.println(e.getMessage());
                    }
                    break;

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