package imageAnalyzer.sift;

import imageAnalyzer.Match;
import org.apache.commons.io.FileUtils;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_features2d.DescriptorMatcher;
import org.bytedeco.opencv.opencv_features2d.SIFT;
import org.bytedeco.opencv.global.opencv_imgcodecs;

import java.io.File;
import java.util.*;

public class SiftAnalyzer {
    // Threshold for considering a match as "good" according to the Lowe ratio test.
    private static final float RATIO_THRESH = 0.75f;
    // Min coincidences between images to consider them equal
    // Can be changed
    private static final int MIN_GOOD_MATCHES = 30;

    // Saves internal information for each image (path to img, SIFT descriptor)
    private record SiftData(String path, Mat descriptors) {}


    public List<Match> analyse(String studentsPath, String excludedPath, String referencePath) {
        System.out.println("[*] Starting SIFT analyse...");
        System.out.println("[INFO] Can take some minutes to initialise");
        SIFT sift = SIFT.create();

        System.out.println("[*] Loading images...");

        // Load and process all images
        List<SiftData> studentImages = loadImages(studentsPath, sift);
        List<SiftData> excludedImages = (excludedPath != null && !excludedPath.isEmpty()) ? loadImages(excludedPath, sift) : Collections.emptyList();
        List<SiftData> referenceImages = (referencePath != null && !referencePath.isEmpty()) ? loadImages(referencePath, sift) : Collections.emptyList();

        System.out.format("[*] Loaded %d students descriptors, %d excluded, %d referenced%n",
                studentImages.size(), excludedImages.size(), referenceImages.size());


        System.out.println("[*] Comparing images between students...");
        long totalStudentPairs = (long) studentImages.size() * (studentImages.size() - 1) / 2;
        long studentPairsProcessed = 0;

        List<Match> matches = new ArrayList<>();
        Set<String> processedPairs = new HashSet<>(); // Avoid A->B y B->A

        // Compare images between students
        for (int i = 0; i < studentImages.size(); i++) {
            for (int j = i + 1; j < studentImages.size(); j++) {
                studentPairsProcessed++;
                printProgress(studentPairsProcessed, totalStudentPairs);

                SiftData img1 = studentImages.get(i);
                SiftData img2 = studentImages.get(j);

                // Avoid to compare images from the same student
                String student1 = new File(img1.path()).getParent();
                String student2 = new File(img2.path()).getParent();
                if (student1.equals(student2)) continue;

                // Key to avoid duplicates
                // The paths are sorted alphabetically so that the key is the same for (A,B) and (B,A)
                String pairKey = img1.path().compareTo(img2.path()) < 0 ?
                        img1.path() + "||" + img2.path() :
                        img2.path() + "||" + img1.path();

                // If the pair of images have already been processed, it continues
                if(processedPairs.contains(pairKey)) continue;


                // If are similar and img1 or img2 are not excluded images --> creates hash + save match + save pairKey
                if (areImagesSimilar(img1, img2) && !isExcluded(img1, excludedImages) && !isExcluded(img2, excludedImages)) {
                    String matchHash = "SIFT_MATCH_" + UUID.randomUUID().toString().substring(0, 8);
                    matches.add(new Match(student1, matchHash, img1.path(), Set.of(img2.path())));
                    processedPairs.add(pairKey);
                }
            }
        }



        if(referencePath != null) {
            System.out.println("[*] Comparing student images with reference images...");

            long totalReferencePairs = (long) studentImages.size() * referenceImages.size();
            long referencePairsProcessed = 0;

            // Compare student images with reference images
            for (SiftData studentImg : studentImages) {
                if (isExcluded(studentImg, excludedImages)) {
                    referencePairsProcessed += referenceImages.size();
                    continue;
                }

                for (SiftData refImg : referenceImages) {
                    referencePairsProcessed++;
                    printProgress(referencePairsProcessed, totalReferencePairs);

                    if (areImagesSimilar(studentImg, refImg)) {
                        String studentPath = new File(studentImg.path()).getParent();
                        String matchHash = "SIFT_REF_MATCH_" + UUID.randomUUID().toString().substring(0, 20);
                        matches.add(new Match(studentPath, matchHash, studentImg.path(), Set.of(refImg.path())));
                    }
                }
            }
        }

        System.out.println("\n[*] SIFT analysed finished");
        return matches;
    }


    // Load all images from the directory and calculates SIFT descriptor for all images
    private List<SiftData> loadImages(String path, SIFT sift) {
        File folder = new File(path);
        if (!folder.exists() || !folder.isDirectory()) {
            return Collections.emptyList();
        }


        List<SiftData> siftDataList = new ArrayList<>();
        Iterable<File> files = FileUtils.listFiles(folder, new String[]{"jpg", "png", "jpeg"}, true);

        for (File file : files) {
            Mat img = opencv_imgcodecs.imread(file.getAbsolutePath(), opencv_imgcodecs.IMREAD_GRAYSCALE);
            if (img.empty()) continue;

            KeyPointVector keypoints = new KeyPointVector();
            Mat descriptors = new Mat();
            sift.detectAndCompute(img, new Mat(), keypoints, descriptors);

            if (!descriptors.empty()) siftDataList.add(new SiftData(file.getAbsolutePath(), descriptors));

        }
        return siftDataList;
    }


    private boolean areImagesSimilar(SiftData data1, SiftData data2) {
        if (data1.descriptors.empty() || data2.descriptors.empty()) return false;

        // Using FLANNBASED algorithm (Fast Library for Approximate Nearest Neighbors)
        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.FLANNBASED);
        DMatchVectorVector knnMatches = new DMatchVectorVector();
        // 2 neighbours to apply Lowe ratio test
        matcher.knnMatch(data1.descriptors, data2.descriptors, knnMatches, 2);

        int goodMatchesCount = 0;
        for (long i=0; i< knnMatches.size(); i++) {
            DMatchVector match = knnMatches.get(i);

            if (match.size() > 1) {
                DMatch m = match.get(0);    // Best coincidence
                DMatch n = match.get(1);    // Second best coincidence
                if (m.distance() < RATIO_THRESH * n.distance()) goodMatchesCount++;
            }
        }

        return goodMatchesCount >= MIN_GOOD_MATCHES;
    }


    private boolean isExcluded(SiftData image, List<SiftData> excludedImages) {
        for (SiftData excluded : excludedImages) {
            if (areImagesSimilar(image, excluded)) return true;
        }
        return false;
    }


    private void printProgress(long current, long total) {
        if (total == 0) return;
        int percent = (int) (current * 100 / total);
        String bar = String.join("", Collections.nCopies(percent / 2, "="));
        String spaces = String.join("", Collections.nCopies(50 - percent / 2, " "));
        System.out.print("\r[" + bar + spaces + "] " + percent + "%");
    }
}
