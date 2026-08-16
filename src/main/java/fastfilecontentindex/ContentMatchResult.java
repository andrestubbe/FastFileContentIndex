package fastfilecontentindex;

public record ContentMatchResult(
        String filePath,
        int lineNumber,
        int charOffset,
        String lineSnippet,
        long searchTimeNs
) {
    @Override
    public String toString() {
        return String.format("%s:%d:%d -> %s", filePath, lineNumber, charOffset, lineSnippet.trim());
    }
}
