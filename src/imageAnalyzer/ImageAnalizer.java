package imageAnalyzer;

import apache.pdfbox.tools.ExtractImages;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class ImageAnalizer {
    private String filesPath;   // Each student must have his subdirectory in the main path
    private String excludedImagePath;

    private Map<String, String> excludedHashes = new HashMap<>();
    private Map<String, String> existingHashes = new HashMap<>();
    private Map<String, Set<String>> currentHashes = new HashMap<>();

    public void analyzer(){
        Scanner scanner = new Scanner(System.in);
        System.out.println("Path to files: ");
        this.filesPath = scanner.nextLine();

        System.out.println("Exclude some images (y/n): ");
        char decision = scanner.next().charAt(0);
        while(decision != 'y' && decision != 'n'){
            System.out.println("[ERROR] Invalid option\nExclude some images (y/n): ");
            decision = scanner.next().charAt(0);
        }

        // If there is a dir with images that must be excluded
        if(decision == 'y'){
            System.out.println("Path excluded images: ");
            this.excludedImagePath = scanner.nextLine();
            this.excludedHashes = hashImages(new File(this.excludedImagePath));
        }

        extractImages(this.filesPath);

    }


    private void extractImages(String path){
        extractImages(new File(path));
    }

    // Save the image in the same dir as the document was
    private void extractImages(File f){
        Iterable<File> files = FileUtils.listFiles(f, new String[]{"pdf", "docx"}, true);

        for(File file : files){
            String ext = FilenameUtils.getExtension(file.getName()).toLowerCase();
            try {
                if(ext.equals("pdf")){
                    ExtractImages.main(new String[]{file.getAbsolutePath()});
                } else if(ext.equals("docx")){
                    try (FileInputStream fis = new FileInputStream(file);
                         XWPFDocument word = new XWPFDocument(fis)) {

                        List<XWPFPictureData> images = word.getAllPictures();
                        int i = 1;
                        for(XWPFPictureData image : images){
                            String extension = image.suggestFileExtension();
                            byte[] data = image.getData();
                            File outImage = new File(file.getParent(), "image" + i + "." + extension);
                            try (FileOutputStream fos = new FileOutputStream(outImage)) {
                                fos.write(data);
                            }
                            i++;
                        }
                    }
                }
            } catch (IOException e) {
                System.err.format("%n---------------%n[ERROR] Failed to extract images from file %s, ignoring file, possibly corrupted. Exception message: %s%n---------------%n%n%n", file, e.getLocalizedMessage());
            }
        }
    }


    private Map<String, String> hashImages(File path){

    }
}
