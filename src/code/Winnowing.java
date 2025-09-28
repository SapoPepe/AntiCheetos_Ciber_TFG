package code;
import java.util.*;


public class Winnowing {
    private final int k;        // Lenght of k-grams
    private final int win;      // Size of the window

    // Param for hash function
    private final int base = 257;
    private final long mod = 1000000007;

    public Winnowing(int k, int win) {
        this.k = k;
        this.win = win;
    }

    /*public List<FinguerPrint> analyse(String docID, String text){
        if(text.length() < k) return Collections.emptyList();

        List<Long> hashes = rollingHashes(text);

        return selectFingerPrints(docID, hashes);
    }*/


    // Calculates the hashes of k-grams from the text using the rolling-hash algorithm
    private List<Long> rollingHashes(String text){
        List<Long> hashes = new ArrayList<>();
        long actualHash = 0;
        long baseK = 1;         // base^k % mod

        // Hash of the first k-gram
        for(int i=0; i<k; i++){
            actualHash = (actualHash * base + text.charAt(i)) % mod;
            if(i < k-1) baseK = (baseK * base) % mod;
        }

        hashes.add(actualHash);

        // Hashes of the rest k-grams using the rolling-hash algorithm
        for (int i = 1; i <= text.length() - k; i++) {
            char prevChar = text.charAt(i - 1);
            char nextChar = text.charAt(i + k - 1);

            // Rest the previous char and add the next char
            actualHash = (actualHash - (prevChar * baseK) % mod + mod) % mod;
            actualHash = (actualHash * base) % mod;
            actualHash = (actualHash + nextChar) % mod;
            hashes.add(actualHash);
        }
        return hashes;
    }

}
