package fastfilecontentindex;

import fastbytes.FastBytes;
import java.nio.charset.StandardCharsets;

public final class FastContentScanner {

    private FastContentScanner() {}

    /**
     * SIMD / AVX2 accelerated substring candidate scanner.
     */
    public static int findSubstringSIMD(byte[] source, byte[] needle, int fromIndex) {
        if (source == null || needle == null || needle.length == 0 || fromIndex >= source.length) {
            return -1;
        }

        byte firstByte = needle[0];
        int maxIndex = source.length - needle.length;

        for (int i = fromIndex; i <= maxIndex; i++) {
            // FastSIMD / FastBytes AVX2 search for the first byte
            int matchPos = FastBytes.indexOf(source, firstByte, i);
            if (matchPos == -1 || matchPos > maxIndex) {
                return -1;
            }

            // Verify full needle match
            boolean fullMatch = true;
            for (int j = 1; j < needle.length; j++) {
                if (source[matchPos + j] != needle[j]) {
                    fullMatch = false;
                    break;
                }
            }

            if (fullMatch) {
                return matchPos;
            }

            i = matchPos;
        }
        return -1;
    }

    public static int findSubstringSIMD(String source, String needle) {
        if (source == null || needle == null || needle.isEmpty()) return -1;
        byte[] srcBytes = source.toLowerCase().getBytes(StandardCharsets.UTF_8);
        byte[] ndlBytes = needle.toLowerCase().getBytes(StandardCharsets.UTF_8);
        return findSubstringSIMD(srcBytes, ndlBytes, 0);
    }
}
