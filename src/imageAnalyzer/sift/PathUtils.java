package imageAnalyzer.sift;


import java.io.File;
import java.text.Normalizer;
import java.util.regex.Pattern;

public class PathUtils {
    private static final Pattern ASCII_PATTERN = Pattern.compile("^\\p{ASCII}*$");

    public static boolean isCompatiblePath(File f){
        if(f == null || !f.exists()) return false;

        return ASCII_PATTERN.matcher(f.getParent()).matches();
    }

    public static boolean isCompatibleFileName(File f){
        if(f == null || !f.exists()) return false;

        return ASCII_PATTERN.matcher(f.getName()).matches();
    }

    public static File sanitizeName(File f){
        // Separates base characters of their accents "á" --> "a" + "´"
        String normalized = Normalizer.normalize(f.getName(), Normalizer.Form.NFD);
        // Delete accents
        String newName = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}", "");
        File newFile = new File(f.getParent(), newName);

        if(f.renameTo(newFile)){
            System.err.format("[INFO] File renamed to: %s%n", newFile.getName());
            return newFile;
        } else {
            System.err.format("[ERROR] File \"%s\" could not be renamed. Process with SIFT could cause an error.%n", f.getName());
            return f;
        }

    }
}
