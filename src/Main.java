import ctfd.CheckFlags;
import ctfd.FlagCopied;
import domjudgeDownloader.SsoLogin;
import imageAnalyzer.ImageAnalizer;
import imageAnalyzer.Match;
import moss.MossInvoker;
import domjudgeDownloader.DomjudgeDownloader;
import org.apache.commons.io.FilenameUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Stream;


public class Main {
    public static void main(String[] args) {
        //System.setProperty("log4j.skipJansi", "true");
        //System.setProperty("log4j2.disable.reflection", "true");

        MossInvoker moss = new MossInvoker();

        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);

        boolean exit = false;

        while (!exit) {
            System.out.println("----------------------------------------");
            System.out.print("1. Domjudge file analysis\n2. Image file analysis\n3. Check flags\n4. Exit\nInsert option: ");
            int option = scanner.nextInt();
            scanner.nextLine();
            switch (option) {
                case 1:
                    System.out.println("[INFO] Is needed the MOSS script in the same directory as AntiCheetos");
                    System.out.print("Base URL (ej: https://yourhost.com): ");
                    String baseApi = scanner.nextLine();
                    System.out.print("External contest ID: ");
                    String cid = scanner.nextLine();

                    String[] cookies = SsoLogin.getCookies(baseApi);
                    if (cookies == null) {
                        System.err.println("[ERROR] Error. Could not get cookies");
                        break;
                    }

                    DomjudgeDownloader domjudge = new DomjudgeDownloader(baseApi, cookies[0], cookies[1]);

                    Path downloads;
                    try {
                        downloads = domjudge.downloader(cid);
                    } catch (Exception e){
                        System.err.println("[ERROR] Error while domjudgeDownloader working\n" + e.getMessage());
                        break;
                    }


                    // Find the language of problem files (one problem might have more than 1 language)
                    Map<Path, List<String>> problemLanguages = moss.findProblemLanguage(downloads);

                    // For each problem we generate petitions to moss
                    try(Stream<Path> problems = Files.list(downloads)){
                        problems.filter(Files::isDirectory)
                                .forEach(problem -> {
                                    List<String> languages = problemLanguages.get(problem);
                                    if(languages == null || languages.isEmpty()) return;

                                    try (Stream<Path> allFiles = Files.list(problem)){
                                        // Get all files in the problem directory
                                        List<Path> allFilesList = allFiles.filter(Files::isRegularFile).toList();

                                        // For each language, filter files by extension and call MOSS
                                        for(String language : languages){
                                            // Filter files that match this language
                                            List<String> filesForLanguage = allFilesList.stream()
                                                    .filter(file -> {
                                                        String extension = FilenameUtils.getExtension(file.getFileName().toString());
                                                        String mappedLanguage = moss.mapExtensionToMoss(extension);
                                                        return language.equals(mappedLanguage);
                                                    })
                                                    .map(path -> path.toAbsolutePath().toString())
                                                    .toList();

                                            // Only call MOSS if there are files for this language
                                            if(!filesForLanguage.isEmpty()){
                                                System.out.println("-----------------------------------------------------------------------------");
                                                System.out.println("Submissions result of \"" + problem.getFileName() + "\" [" + language + "]");
                                                System.out.println("Files to analyze: " + filesForLanguage.size());
                                                moss.run(language, filesForLanguage);
                                            }
                                        }

                                    } catch (Exception e){
                                        System.err.println("[ERROR] Files from " + problem.getFileName() + " couldn't be read");
                                    }
                                });
                    } catch (Exception e){
                        System.err.println("[ERROR] Directory ./downloads can't be read");
                    }
                    break;

                case 2:
                    ImageAnalizer ia = new ImageAnalizer();
                    try{
                        List<Match> matches = ia.analyzer();
                        System.out.println("----------------------------------------\n> RESULTS\n----------------------------------------");
                        for (var m : matches) {
                            System.out.println(m);
                        }
                        System.out.println("----------------------------------------\n> End of results");
                    } catch (Exception e){
                        System.err.println("[ERROR] Could not execute image analyzer");
                        e.printStackTrace();
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