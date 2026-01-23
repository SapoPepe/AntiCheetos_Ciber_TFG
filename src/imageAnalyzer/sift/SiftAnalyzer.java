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

    // Final threshold to decide if is a copy
    private static final double FINAL_DECISION_THRESHOLD = 75.0;

    private record SiftData(String path, Mat descriptors) {}

    // Group similar images class
    private static class UnionFind {
        private final Map<String, String> parent = new HashMap<>();

        public void add(String path) {
            parent.putIfAbsent(path, path);
        }

        public String find(String path) {
            if (!parent.containsKey(path)) {
                parent.put(path, path);
                return path;
            }

            String root = path;
            while (!parent.get(root).equals(root)) {
                root = parent.get(root);
            }
            
            String current = path;
            while (!parent.get(current).equals(root)) {
                String next = parent.get(current);
                parent.put(current, root);
                current = next;
            }

            return root;
        }

        public void union(String path1, String path2) {
            String root1 = find(path1);
            String root2 = find(path2);

            if (!root1.equals(root2)) {
                // Alphabetically minor is the root
                if (root1.compareTo(root2) < 0) parent.put(root2, root1);
                else parent.put(root1, root2);
            }
        }

        public Map<String, Set<String>> getClusters() {
            Map<String, Set<String>> clusters = new HashMap<>();

            for (String path : parent.keySet()) {
                String root = find(path);
                clusters.computeIfAbsent(root, k -> new HashSet<>()).add(path);
            }

            return clusters;
        }
    }

    public List<Match> analyse(String studentsPath, String excludedPath, String referencePath) {
        System.out.println("[*] Starting SIFT analysis...");
        SIFT sift = SIFT.create();

        // Load images
        System.out.println("[*] Loading images...");
        List<SiftData> studentImages = loadImages(studentsPath, sift);
        List<SiftData> excludedImages = (excludedPath != null && !excludedPath.isEmpty()) ? loadImages(excludedPath, sift) : Collections.emptyList();
        List<SiftData> referenceImages = (referencePath != null && !referencePath.isEmpty()) ? loadImages(referencePath, sift) : Collections.emptyList();

        System.out.format("[*] Loaded %d students, %d excluded, %d referenced%n", studentImages.size(), excludedImages.size(), referenceImages.size());

        UnionFind uf = new UnionFind();

        long totalPairs = (long) studentImages.size() * (studentImages.size() - 1) / 2;
        long processed = 0;

        System.out.println("[*] Comparing images...");

        // Compare students images
        for (int i = 0; i < studentImages.size(); i++) {
            for (int j = i + 1; j < studentImages.size(); j++) {
                processed++;
                printProgress(processed, totalPairs);

                SiftData img1 = studentImages.get(i);
                SiftData img2 = studentImages.get(j);

                // Avoid comparing a student to himself
                String student1 = new File(img1.path()).getParent();
                String student2 = new File(img2.path()).getParent();
                if (student1.equals(student2)) continue;

                // If they are not excluded and are similar, join them in the same cluster
                if (areImagesSimilar(img1, img2) && !isExcluded(img1, excludedImages) && !isExcluded(img2, excludedImages)) {
                    uf.add(img1.path());
                    uf.add(img2.path());
                    uf.union(img1.path(), img2.path());
                }
            }
        }

        // Comparing new images with references
        if (referencePath != null && !referenceImages.isEmpty()) {
            System.out.println("\n[*] Comparing with references...");
            for (SiftData studentImg : studentImages) {
                if (isExcluded(studentImg, excludedImages)) continue;

                for (SiftData refImg : referenceImages) {
                    if (areImagesSimilar(studentImg, refImg)) {
                        uf.add(studentImg.path());
                        uf.add(refImg.path());
                        uf.union(studentImg.path(), refImg.path());
                    }
                }
            }
        }


        System.out.println("\n[*] Building final report...");
        // Obtain clusters and transform to matches
        Map<String, Set<String>> clusters = uf.getClusters();
        List<Match> finalList = new ArrayList<>();

        for (Map.Entry<String, Set<String>> entry : clusters.entrySet()) {
            Set<String> cluster = entry.getValue();

            // Match = cluster.size() > 1
            if (cluster.size() > 1) {
                // Principal file is the one who is alphabetically first, the rest are copies
                String mainFile = entry.getKey();
                Set<String> copies = new HashSet<>(cluster);
                copies.remove(mainFile);

                String studentName = new File(mainFile).getParent();
                String hash = "SIFT_" + UUID.randomUUID().toString().substring(0, 8);

                finalList.add(new Match(studentName, hash, mainFile, copies));
            }
        }

        System.out.println("[*] Done. Total matches found: " + finalList.size());
        return finalList;
    }




    private boolean areImagesSimilar(SiftData data1, SiftData data2) {
        if (data1.descriptors.empty() || data2.descriptors.empty()) return false;

        // --- PAIRING WITH FLANN ---
        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.FLANNBASED);
        DMatchVectorVector knnMatches = new DMatchVectorVector();
        matcher.knnMatch(data1.descriptors, data2.descriptors, knnMatches, 2);

        long totalMatches = knnMatches.size();
        if (totalMatches == 0) return false;

        // 1. Match quality analysis (distance < 1.0)
        int perfectMatchesCount = 0;
        for (long i = 0; i < totalMatches; i++) {
            DMatchVector match = knnMatches.get(i);
            if (match.size() > 0 && match.get(0).distance() < 1.0f) {
                perfectMatchesCount++;
            }
        }

        double ratioPerfect = (double) perfectMatchesCount / totalMatches;
        boolean isSameImage = ratioPerfect > 0.85;
        float loweThreshold = isSameImage ? 0.95f : 0.75f; // Dynamic threshold

        // 2. Apply Lowe's ratio test and accumulate distances for quality score
        int goodMatchesCount = 0;
        double sumDistances = 0.0;

        for (long i = 0; i < totalMatches; i++) {
            DMatchVector match = knnMatches.get(i);
            if (match.size() > 1) {
                DMatch m = match.get(0);
                DMatch n = match.get(1);
                if (m.distance() < loweThreshold * n.distance()) {
                    goodMatchesCount++;
                    sumDistances += m.distance();
                }
            }
        }

        // --- SIMILARITY CALCULATION ---
        long lenKp1 = data1.descriptors.rows();
        long lenKp2 = data2.descriptors.rows();

            // Similarity over average
            double avgKeypoints = (lenKp1 + lenKp2) / 2.0;
            double similarityAvg = Math.min(100.0, (goodMatchesCount / avgKeypoints) * 100.0);

            // Similarity over max
            double maxKeypoints = Math.max(lenKp1, lenKp2);
            double similarityMax = (goodMatchesCount / maxKeypoints) * 100.0;

            // Quality over average
            double avgDistance = (goodMatchesCount > 0) ? (sumDistances / goodMatchesCount) : Double.MAX_VALUE;
            double qualityScore = (goodMatchesCount > 0) ? Math.max(0.0, 100.0 - avgDistance) : 0.0;

            // Perfect similarity
            double similarityPerfect = (double) perfectMatchesCount / totalMatches * 100.0;

            // Keypoints ratio for detecting imbalances
            double minKp = Math.min(lenKp1, lenKp2);
            double keypointRatio = (maxKeypoints > 0) ? minKp / maxKeypoints : 1.0;


        // --- INTELLIGENT DECISION ---
        double finalSimilarityPercentage;
        if (isSameImage && similarityPerfect > 95.0) {
            // Identical
            finalSimilarityPercentage = 100.0;
        } else if (similarityPerfect > 50.0) {
            // Very similar / identical
            finalSimilarityPercentage = Math.max(similarityAvg, similarityPerfect);
        } else {
            // Different
            // Average between quantity and quality
            finalSimilarityPercentage = (similarityMax + qualityScore) / 2.0;
            // Keypoint imbalance adjustment
            if (keypointRatio < 0.5) finalSimilarityPercentage *= (0.5 + keypointRatio / 2.0);
        }


        return finalSimilarityPercentage > FINAL_DECISION_THRESHOLD;
    }




    private List<SiftData> loadImages(String path, SIFT sift) {
        File folder = new File(path);
        if (!folder.exists() || !folder.isDirectory()) return Collections.emptyList();

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

    private boolean isExcluded(SiftData image, List<SiftData> excludedImages) {
        for (SiftData excluded : excludedImages) {
            if (areImagesSimilar(image, excluded)) return true;
        }
        return false;
    }

    private void printProgress(long current, long total) {
        if (total == 0) return;
        if (current % (Math.max(1, total/20)) == 0) {
            int percent = (int) (current * 100 / total);
            System.out.print("\rProgress: " + percent + "%");
        }
    }
}