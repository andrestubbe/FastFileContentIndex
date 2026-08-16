package fastfilecontentindex;

/**
 * High-performance 64-bit Bloom filter for 3-gram substring testing.
 */
public final class TrigramBloomFilter {

    private long bitmask;

    public TrigramBloomFilter() {
        this.bitmask = 0L;
    }

    public TrigramBloomFilter(long bitmask) {
        this.bitmask = bitmask;
    }

    public void addTrigram(int char1, int char2, int char3) {
        int hash1 = hashTrigram(char1, char2, char3);
        int hash2 = hashTrigram(char3, char2, char1);

        bitmask |= (1L << (hash1 & 63));
        bitmask |= (1L << (hash2 & 63));
    }

    public boolean mightContainTrigram(int char1, int char2, int char3) {
        int hash1 = hashTrigram(char1, char2, char3);
        int hash2 = hashTrigram(char3, char2, char1);

        long mask = (1L << (hash1 & 63)) | (1L << (hash2 & 63));
        return (bitmask & mask) == mask;
    }

    public boolean mightContainQuery(String query) {
        if (query == null || query.length() < 3) {
            return true; // Fallback to linear scan for very short queries
        }
        query = query.toLowerCase();
        for (int i = 0; i <= query.length() - 3; i++) {
            char c1 = query.charAt(i);
            char c2 = query.charAt(i + 1);
            char c3 = query.charAt(i + 2);
            if (!mightContainTrigram(c1, c2, c3)) {
                return false; // Guaranteed not in file block
            }
        }
        return true;
    }

    public static TrigramBloomFilter buildFromBytes(byte[] bytes) {
        TrigramBloomFilter filter = new TrigramBloomFilter();
        if (bytes == null || bytes.length < 3) {
            return filter;
        }
        for (int i = 0; i <= bytes.length - 3; i++) {
            filter.addTrigram(bytes[i] & 0xFF, bytes[i + 1] & 0xFF, bytes[i + 2] & 0xFF);
        }
        return filter;
    }

    public static TrigramBloomFilter buildFromText(String text) {
        TrigramBloomFilter filter = new TrigramBloomFilter();
        if (text == null || text.length() < 3) {
            return filter;
        }
        String lower = text.toLowerCase();
        for (int i = 0; i <= lower.length() - 3; i++) {
            filter.addTrigram(lower.charAt(i), lower.charAt(i + 1), lower.charAt(i + 2));
        }
        return filter;
    }

    public long getBitmask() {
        return bitmask;
    }

    private static int hashTrigram(int c1, int c2, int c3) {
        int h = (c1 * 31 + c2) * 31 + c3;
        h ^= (h >>> 16);
        return h & 0x7FFFFFFF;
    }
}
