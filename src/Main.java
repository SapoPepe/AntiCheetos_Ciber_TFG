import moss.MossInvoker;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        // Moss configuration
        MossInvoker moss = new MossInvoker();
        Set<String> mossLanguages = Set.of("c", "c++", "c#", "java", "python", "mips assembly", "a8086 assembly");

        Scanner scan = new Scanner(System.in);

        boolean exit = false;

        while (!exit) {
            int option = scan.nextInt();

            switch (option) {
                case 1:
                    System.out.print("Insert path to directory: ");
                    Path dirPath = Paths.get(scan.nextLine());
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
                            String lan = scan.nextLine().toLowerCase();
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