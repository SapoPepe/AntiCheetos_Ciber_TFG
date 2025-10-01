package imageAnalyzer;

import java.util.Set;

public record Match(String student, String hash, String file, Set<String> otherMatches) {}

