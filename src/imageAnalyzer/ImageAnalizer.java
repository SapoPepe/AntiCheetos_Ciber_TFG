package imageAnalyzer;


import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.Level;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.logging.log4j.core.config.Configurator;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;


public class ImageAnalizer {
    private static final String EMPTY_SHA1 = "DA39A3EE5E6B4B0D3255BFEF95601890AFD80709";

    private String filesPath;   // Each student must have his subdirectory in the main path
    private String excludedImgPath;
    private String referenceImgPath;

    private Map<String, String> excludedHashes = new HashMap<>();
    private Map<String, String> existingHashes = new HashMap<>();
    private Map<String, Set<String>> currentHashes = new HashMap<>();

    private String currentPath = new File("").getAbsolutePath();

    public List<Match> analyzer() throws Exception {
        Configurator.setLevel("org.apache.pdfbox", Level.ERROR);
        Scanner scanner = new Scanner(System.in);

        // Analysis options
        System.out.print("Analysis options:\n\t1) Data + metadata\n\t2) Data\n\t3) SIFT\nOption: ");
        int vision = scanner.nextInt();
        scanner.nextLine();
        while(vision < 1 || vision > 3){
            System.out.print("[ERROR] Invalid option. Choose an other: ");
            vision = scanner.nextInt();
        }


        System.out.print("Path to students dir: ");
        this.filesPath = scanner.nextLine();


        // Excluded images
        System.out.print("Exclude some images (y/n): ");
        char decision = scanner.nextLine().charAt(0);
        while(decision != 'y' && decision != 'n'){
            System.out.print("[ERROR] Invalid option. Exclude some images (y/n): ");
            decision = scanner.nextLine().charAt(0);
        }
        if(decision == 'y'){
            System.out.print("Path excluded images: ");
            this.excludedImgPath = scanner.nextLine();
            if(vision!=3){
                this.excludedHashes = parseFolder(new File(this.excludedImgPath), vision);
                System.out.format("[*] Loaded %s excluded files in %s %n", this.excludedHashes.size(), this.excludedImgPath);
            }
        }

        // Reference images
        // PDFs from which images will be extracted and used to compare against, but they themselves should not be compared against the rest
        System.out.print("Extract image references from other files (y/n): ");
        decision = scanner.next().charAt(0);
        while(decision != 'y' && decision != 'n'){
            System.out.print("[ERROR] Invalid option. Extract image references from other files (y/n): ");
            decision = scanner.nextLine().charAt(0);
        }
        if(decision == 'y'){
            System.out.println("Path reference files: ");
            this.referenceImgPath = scanner.nextLine();
            extractImages(this.referenceImgPath);
            System.out.println("[*] Img extraction of reference files completed");
            if(vision!=3) {
                this.existingHashes = parseFolder(new File(this.referenceImgPath), vision);
                System.out.format("[*] Analyzed %s existing files in %s %n", this.existingHashes.size(), this.referenceImgPath);
            }
        }





        Function<String, HashSet<String>> hashsetCreator = (s) -> new HashSet<>();
        List<File> studentFolders = Files.list(Path.of(filesPath))
                                            .map(Path::toFile)
                                            .filter(File::isDirectory)
                                            .toList();

        ArrayList<Student> students = new ArrayList<>(studentFolders.size());
        for (File folder : studentFolders) {
            extractImages(folder);
            students.add(loadStudent(folder, vision));
        }
        System.out.println("[*] Students loaded");

        // SIFT analysed, added as module for the original code
        if (vision == 3) {
            SiftAnalyzer sift = new SiftAnalyzer();
            return sift.analyse(this.filesPath, this.excludedImgPath, this.referenceImgPath);
        }

        // Prepare dataset
        for(Student s : students){
            for(Map.Entry<String, String> e : s.files().entrySet()){
                String hash = e.getKey();
                this.currentHashes.computeIfAbsent(hash, hashsetCreator);
                this.currentHashes.get(hash).add(e.getValue());
            }
        }


        // Analysis
        List<Match> matches = new ArrayList<>();
        Set<String> processedHashes = new HashSet<>();
        for(Student s : students){
            for(Map.Entry<String, String> e : s.files().entrySet()){
                String hash = e.getKey();

                if(this.excludedHashes.containsKey(hash)) continue;

                if(this.existingHashes.containsKey(hash)){
                    matches.add(new Match(s.path(), hash, e.getValue(), Set.of(this.existingHashes.get(hash))));
                }

                Set<String> fileHashes = this.currentHashes.get(hash);
                if(fileHashes.size() > 1 && !processedHashes.contains(hash)){
                    HashSet<String> otherNames = new HashSet<>(fileHashes);
                    otherNames.remove(e.getValue());

                    matches.add(new Match(s.path(), hash, e.getValue(), otherNames));
                    processedHashes.add(hash);
                }
            }
        }
        System.out.println("[*] Analysis completed");


        return matches;

    }


    private void extractImages(String path){
        extractImages(new File(path));
    }

    // Save the image in the same dir as the document was
    private void extractImages(File f){
        Iterable<File> files = FileUtils.listFiles(f, new String[]{"pdf", "docx"}, false);

        for(File file : files){
            String ext = FilenameUtils.getExtension(file.getName()).toLowerCase();
            try {
                if(ext.equals("pdf")){
                    //ExtractImages.main(new String[]{file.getAbsolutePath()});
                    try (PDDocument document = PDDocument.load(file)) {
                        int imageCounter = 1;
                        String baseName = FilenameUtils.getBaseName(file.getName());

                        for (PDPage page : document.getPages()) {
                            imageCounter = processResources(page.getResources(), file.getParentFile(), baseName, imageCounter);
                        }
                    }


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





    private int processResources(PDResources resources, File outputDir, String baseName, int imageCounter) throws IOException {
        if (resources == null) return imageCounter;

        for (org.apache.pdfbox.cos.COSName xObjectName : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(xObjectName);

            if (xObject instanceof PDImageXObject) {
                PDImageXObject image = (PDImageXObject) xObject;
                String extension = image.getSuffix() != null ? image.getSuffix() : "png";
                File outputFile = new File(outputDir, baseName + "_" + imageCounter + "." + extension);
                ImageIO.write(image.getImage(), extension, outputFile);
                imageCounter++;
            } else if (xObject instanceof PDFormXObject) {
                // If it is a formulary (container), search for images recursively inside of it
                PDFormXObject form = (PDFormXObject) xObject;
                imageCounter = processResources(form.getResources(), outputDir, baseName, imageCounter);
            }
        }
        return imageCounter;
    }





    // Return Map<hash, filePath>
    private Map<String, String> parseFolder(File path, int method){
        Iterable<File> files = FileUtils.listFiles(path, new String[]{"jpg", "png", "jpeg"}, true);

        Map<String, String> result = new HashMap<>();
        for(File img : files){
            // HashedFile hashedFile = visualComparation ? sha1Img(img) : sha1File(img);
            HashedFile hashedFile = switch (method) {
                // Data + metadata
                case 1 -> sha1File(img);
                // Data
                case 2 -> sha1Img(img);
                default -> null;
            };

            if(hashedFile != null && !hashedFile.hash.equals(EMPTY_SHA1)) result.put(hashedFile.hash(), hashedFile.path());
        }

        return result;
    }

    public record HashedFile(String hash, String path){}


    // Hash from image pixels only
    private HashedFile sha1Img(File file){
        String simplifiedPath = file.getAbsolutePath().replace(this.currentPath, "");
        try {

            BufferedImage img = ImageIO.read(file);
            if (img == null) return new HashedFile(EMPTY_SHA1, simplifiedPath);

            int w = img.getWidth(), h = img.getHeight();
            int[] data = img.getRaster().getPixels(0, 0, w, h, (int[]) null);
            byte[] bytes = new byte[data.length * 4];
            for (int i = 0; i < data.length; i+=4) {
                bytes[i+0] = (byte)(data[i] >> 0);
                bytes[i+1] = (byte)(data[i] >> 8);
                bytes[i+2] = (byte)(data[i] >> 16);
                bytes[i+3] = (byte)(data[i] >> 24);
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(bytes);

            var hash = digest.digest();
            var hashString = bytesToHex(hash);
            return new HashedFile(hashString, simplifiedPath);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Hash from the complete file, including metadata, headers...
    private HashedFile sha1File(File file){
        try (InputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");

            int n = 0;
            byte[] buffer = new byte[8192];
            while (n != -1) {
                n = fis.read(buffer);
                if (n > 0) {
                    digest.update(buffer, 0, n);
                }
            }

            var hash = digest.digest();
            var hashString = bytesToHex(hash);
            return new HashedFile(hashString, file.getAbsolutePath().replace(this.currentPath, ""));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }



    private Student loadStudent(File file, int vision) {
        Map<String, String> hashes = parseFolder(file, vision);
        return new Student(file.getName(), hashes);
    }



}
