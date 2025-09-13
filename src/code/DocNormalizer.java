package code;

//-------------------------------------------------------//
// Normalizes the content of a document for the analysis //
// ------------------------------------------------------//
public class DocNormalizer {
    public static String normalizer(String content) {
        String noComments = content.replaceAll("/\\*[\\s\\S]*?\\*/", "")    // Delete block comments
                                    .replaceAll("//\\.\\*", "");            // Delete simple comments

        String noWhiteSpaces = noComments.replaceAll("\\s+", "");           // Delete space, tab, new line...

        return noWhiteSpaces.toLowerCase();                                                  // Lowercase
    }
}

