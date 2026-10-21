package nz.ac.aut.pdc.infrastructure;

import nz.ac.aut.pdc.shared.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** File preparation does not commit a receipt. Unreferenced copies are never silently cleaned. */
public final class AttachmentStore {
    public record Attachment(String attachmentId, String receiptId, String relativePath, String contentHash,
                             String format, long sizeBytes) {}
    private final DataRoot root;
    public AttachmentStore(DataRoot root) { this.root = root; }
    public Result<Attachment> importImage(Path source, String receiptId, int alreadyAttached) {
        if (alreadyAttached < 0 || alreadyAttached >= 3) return Result.rejected("INVALID_INPUT", "At most three attachments");
        try {
            long size = Files.size(source);
            if (size <= 0 || size > 5_000_000) return Result.rejected("INVALID_INPUT", "Image exceeds 5,000,000 bytes or is empty");
            byte[] bytes;
            try (InputStream input = Files.newInputStream(source)) { bytes = input.readNBytes(5_000_001); }
            if (bytes.length <= 0 || bytes.length > 5_000_000) return Result.rejected("INVALID_INPUT", "Invalid image size");
            String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
            String format;
            if (name.endsWith(".png") && png(bytes)) format = "PNG";
            else if ((name.endsWith(".jpg") || name.endsWith(".jpeg")) && jpeg(bytes)) format = "JPG";
            else return Result.rejected("INVALID_INPUT", "JPG/PNG extension and signature required");
            // Header/format verification only, no content judgment or OCR.
            try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) return Result.rejected("INVALID_INPUT", "Unreadable image");
                var reader = readers.next();
                try { reader.setInput(stream); if (reader.getWidth(0) <= 0 || reader.getHeight(0) <= 0) throw new IOException("Invalid dimensions"); }
                finally { reader.dispose(); }
            }
            Files.createDirectories(root.resolve("attachments"));
            String id = UUID.randomUUID().toString(), reference = "attachments/" + id + (format.equals("PNG") ? ".png" : ".jpg");
            Path target = root.resolve(reference);
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Attachment attachment = new Attachment(id, receiptId, reference, JsonCodec.hash(bytes), format, bytes.length);
            verify(root, attachment);
            return Result.prepared(attachment);
        } catch (IOException | RuntimeException e) { return Result.failed("ATTACHMENT_FAILED", "Image copy/verification failed; no successful reference"); }
    }
    private static boolean png(byte[] b) { return b.length >= 8 && Arrays.equals(Arrays.copyOf(b, 8), new byte[]{(byte)137,80,78,71,13,10,26,10}); }
    private static boolean jpeg(byte[] b) { return b.length >= 3 && b[0] == (byte)255 && b[1] == (byte)216 && b[2] == (byte)255; }
    public static void verify(DataRoot root, Attachment a) throws IOException {
        if (a.relativePath() == null || !a.relativePath().startsWith("attachments/") || a.sizeBytes() <= 0 || a.sizeBytes() > 5_000_000)
            throw new IOException("Invalid attachment metadata");
        if (!Set.of("PNG","JPG").contains(a.format()) || a.attachmentId() == null) throw new IOException("Invalid image format/ID");
        try { UUID.fromString(a.attachmentId()); } catch (IllegalArgumentException e) { throw new IOException("Invalid image ID",e); }
        String expected = "attachments/" + a.attachmentId() + (a.format().equals("PNG") ? ".png" : ".jpg");
        if (!expected.equals(a.relativePath())) throw new IOException("Image reference must use its independent ID");
        Path target = root.resolve(a.relativePath());
        if (!Files.isRegularFile(target) || Files.size(target) != a.sizeBytes()) throw new IOException("Attachment missing/size mismatch");
        byte[] bytes;
        try (InputStream input = Files.newInputStream(root.resolve(a.relativePath()))) { bytes = input.readNBytes(5_000_001); }
        if (bytes.length != a.sizeBytes() || a.format().equals("PNG") && !png(bytes) || a.format().equals("JPG") && !jpeg(bytes))
            throw new IOException("Image size/signature mismatch");
        if (!JsonCodec.hash(bytes).equals(a.contentHash())) throw new IOException("Attachment hash mismatch");
    }
}
