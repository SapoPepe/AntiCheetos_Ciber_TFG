package imageAnalyzer.sift;

import imageAnalyzer.Match;
import org.apache.commons.io.FileUtils;
import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_calib3d;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_features2d.DescriptorMatcher;
import org.bytedeco.opencv.opencv_features2d.SIFT;
import org.bytedeco.opencv.opencv_img_hash.PHash;

import java.io.File;
import java.util.*;

public class SiftAnalyzer {
    // Thresholds
    private static final int RANSAC_MIN_INLIERS = 30;
    private static final double RANSAC_THRESHOLD = 4.0;
    private static final double MIN_INLIER_RATIO = 0.25;
    private static final double PHASH_THRESHOLD = 4.0;

    private record SiftData(String path, Mat descriptors, KeyPointVector keypoints, Mat descriptorsFlipped, KeyPointVector keypointsFlipped) {}

    // Group similar images class
    private static class UnionFind {
        private final Map<String, String> parent = new HashMap<>();
        public void add(String path) { parent.putIfAbsent(path, path); }
        public String find(String path) {
            if (!parent.containsKey(path)) { parent.put(path, path); return path; }
            String root = path;
            while (!parent.get(root).equals(root)) root = parent.get(root);
            String current = path;
            while (!parent.get(current).equals(root)) {
                String next = parent.get(current); parent.put(current, root); current = next;
            }
            return root;
        }
        public void union(String path1, String path2) {
            String root1 = find(path1); String root2 = find(path2);
            if (!root1.equals(root2)) {
                if (root1.compareTo(root2) < 0) parent.put(root2, root1); else parent.put(root1, root2);
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
        long startTime = System.currentTimeMillis();

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
                if (checkFullSimilarity(img1, img2) && !isExcluded(img1, excludedImages) && !isExcluded(img2, excludedImages)) {
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
                    if (checkFullSimilarity(studentImg, refImg)) {
                        uf.add(studentImg.path());
                        uf.add(refImg.path());
                        uf.union(studentImg.path(), refImg.path());
                    }
                }
            }
        }

        long endTime = System.currentTimeMillis();
        long elapsedTime = endTime - startTime;
        long seconds = (elapsedTime / 1000) % 60;
        long minutes = (elapsedTime / 1000) / 60;
        System.out.format("\n[*] SIFT analysis completed in %d min %d sec (%d ms)%n", minutes, seconds, elapsedTime);

        return buildReport(uf);
    }


    private boolean checkFullSimilarity(SiftData data1, SiftData data2) {
        // Normal geometry
        if (areImagesSimilar(data1.descriptors, data1.keypoints, data2.descriptors, data2.keypoints)) {
            return true;
        }
        // Mirror geometry
        if (areImagesSimilar(data1.descriptors, data1.keypoints, data2.descriptorsFlipped, data2.keypointsFlipped)) {
            return true;
        }
        // Fallback PHash
        return verifyWithPerceptualHash(data1.path(), data2.path());
    }


    private boolean areImagesSimilar(Mat desc1, KeyPointVector kp1, Mat desc2, KeyPointVector kp2) {
        if (desc1.empty() || desc2.empty()) return false;

        // --- PAIRING WITH FLANN ---
        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.FLANNBASED);
        DMatchVectorVector knnMatches = new DMatchVectorVector();
        matcher.knnMatch(desc1, desc2, knnMatches, 2);

        List<DMatch> goodMatches = new ArrayList<>();
        float ratioThresh = 0.75f;

        for (long i = 0; i < knnMatches.size(); i++) {
            DMatchVector matches = knnMatches.get(i);
            if (matches.size() >= 2) {
                DMatch m = matches.get(0);
                DMatch n = matches.get(1);
                if (m.distance() < ratioThresh * n.distance()) {
                    goodMatches.add(m);
                }
            }
        }

        if (goodMatches.size() < RANSAC_MIN_INLIERS) return false;

        return verifyWithRansac(kp1, kp2, goodMatches);
    }



    private List<SiftData> loadImages(String path, SIFT sift) {
        File folder = new File(path);
        if (!folder.exists() || !folder.isDirectory()) return Collections.emptyList();

        List<SiftData> siftDataList = new ArrayList<>();
        Iterable<File> files = FileUtils.listFiles(folder, new String[]{"jpg", "png", "jpeg"}, true);

        for (File file : files) {
            Mat img = opencv_imgcodecs.imread(file.getAbsolutePath(), opencv_imgcodecs.IMREAD_GRAYSCALE);
            if (img.empty()) continue;

            KeyPointVector kp = new KeyPointVector();
            Mat desc = new Mat();
            sift.detectAndCompute(img, new Mat(), kp, desc);

            Mat imgF = new Mat();
            opencv_core.flip(img, imgF, 1);
            KeyPointVector kpF = new KeyPointVector();
            Mat descF = new Mat();
            sift.detectAndCompute(imgF, new Mat(), kpF, descF);

            if (!desc.empty()) {
                if (descF.empty()) { descF = new Mat(); kpF = new KeyPointVector(); }
                siftDataList.add(new SiftData(file.getAbsolutePath(), desc, kp, descF, kpF));
            }
            img.close(); imgF.close();
        }
        return siftDataList;
    }


    private boolean isExcluded(SiftData image, List<SiftData> excludedImages) {
        for (SiftData excluded : excludedImages) {
            if (checkFullSimilarity(image, excluded)) return true;
        }
        return false;
    }


    private boolean verifyWithRansac(KeyPointVector kp1, KeyPointVector kp2, List<DMatch> goodMatches) {
        int nPoints = goodMatches.size();
        Mat srcMat = new Mat(nPoints, 1, opencv_core.CV_32FC2);
        Mat dstMat = new Mat(nPoints, 1, opencv_core.CV_32FC2);

        FloatIndexer srcIndexer = (FloatIndexer) srcMat.createIndexer();
        FloatIndexer dstIndexer = (FloatIndexer) dstMat.createIndexer();

        for (int i = 0; i < nPoints; i++) {
            DMatch match = goodMatches.get(i);
            Point2f p1 = kp1.get(match.queryIdx()).pt();
            Point2f p2 = kp2.get(match.trainIdx()).pt();
            srcIndexer.put(i, 0, 0, p1.x()); srcIndexer.put(i, 0, 1, p1.y());
            dstIndexer.put(i, 0, 0, p2.x()); dstIndexer.put(i, 0, 1, p2.y());
        }

        Mat mask = new Mat();
        Mat homography = opencv_calib3d.findHomography(srcMat, dstMat, opencv_calib3d.USAC_MAGSAC, RANSAC_THRESHOLD, mask, 2000, 0.995);

        boolean isMatch = false;

        if (!homography.empty()) {
            int inliersCount = opencv_core.countNonZero(mask);
            double inlierRatio = (double) inliersCount / nPoints;

            if (inliersCount >= RANSAC_MIN_INLIERS && inlierRatio >= MIN_INLIER_RATIO) {
                if (validateHomography(homography)) isMatch = true;
            }
        }

        srcMat.close(); dstMat.close(); mask.close(); homography.close();
        return isMatch;
    }


    private boolean validateHomography(Mat homography) {
        // Homography = 3x3 doubles matrix
        // [ h00 h01 h02 ]
        // [ h10 h11 h12 ]
        // [ h20 h21 h22 ]
        DoubleIndexer idx = homography.createIndexer();

        double h00 = idx.get(0, 0);
        double h01 = idx.get(0, 1);
        double h10 = idx.get(1, 0);
        double h11 = idx.get(1, 1);
        double h22 = idx.get(2, 2);

        // Determinant for affine transformations --- det = (scale_x * scale_y) - (skew)
        double det = h00 * h11 - h01 * h10;

        //Validation
        // < 0.05 --- the image is compressed to almost nothing
        // > 20.0 --- the image expands gigantically
        if (Math.abs(det) < 0.05 || Math.abs(det) > 20.0) return false;

        // Extra validation
        if (Math.abs(h22) < 0.001) return false;

        return true;
    }


    private boolean verifyWithPerceptualHash(String path1, String path2) {
        Mat img1 = opencv_imgcodecs.imread(path1, opencv_imgcodecs.IMREAD_GRAYSCALE);
        Mat img2 = opencv_imgcodecs.imread(path2, opencv_imgcodecs.IMREAD_GRAYSCALE);
        if (img1.empty() || img2.empty()) { img1.close(); img2.close(); return false; }

        PHash pHash = PHash.create();
        Mat hash1 = new Mat();
        Mat hash2 = new Mat();
        try {
            pHash.compute(img1, hash1);
            pHash.compute(img2, hash2);
            double distance = pHash.compare(hash1, hash2);
            return distance <= PHASH_THRESHOLD;
        } catch (Exception e) { return false;
        } finally { img1.close(); img2.close(); hash1.close(); hash2.close(); }
    }


    private List<Match> buildReport(UnionFind uf) {
        System.out.println("\n[*] Building final report...");
        Map<String, Set<String>> clusters = uf.getClusters();
        List<Match> finalList = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : clusters.entrySet()) {
            Set<String> cluster = entry.getValue();
            if (cluster.size() > 1) {
                String mainFile = entry.getKey();
                Set<String> copies = new HashSet<>(cluster);
                copies.remove(mainFile);
                finalList.add(new Match(new File(mainFile).getParentFile().getName(),"SIFT_" + UUID.randomUUID().toString().substring(0, 8), mainFile, copies));
            }
        }
        System.out.println("[*] Done. Total matches found: " + finalList.size());
        return finalList;
    }


    private void printProgress(long current, long total) {
        if (total == 0) return;
        if (current % (Math.max(1, total/20)) == 0) {
            int percent = (int) (current * 100 / total);
            System.out.print("\rProgress: " + percent + "%");
        }
    }
}