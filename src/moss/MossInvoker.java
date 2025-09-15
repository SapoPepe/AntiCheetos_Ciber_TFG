package moss;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
}
