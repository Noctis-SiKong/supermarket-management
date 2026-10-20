package nz.ac.aut.pdc.infrastructure;

import nz.ac.aut.pdc.shared.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.*;
import static org.junit.Assert.*;

public class AttachmentTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    DataRoot root;
    @Before public void root() throws Exception {
        Path p = temp.newFolder().toPath(); Files.writeString(p.resolve("README.md"),"fixture");
        Files.writeString(p.resolve("pom.xml"),"fixture"); Files.createDirectory(p.resolve("docs")); root = DataRoot.forProject(p);
    }
    Path image() throws Exception {
        Path p = temp.newFolder().toPath().resolve("虚构 图片.png");
        assertTrue(ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",p.toFile())); return p;
    }
    @Test public void unicodeSourceIsCopiedWithIndependentIdRelativeReferenceAndVerifiedHash() throws Exception {
        Path source = image(); AttachmentStore attachments = new AttachmentStore(root);
        var result = attachments.importImage(source,"receipt",0); assertEquals(Result.Status.PREPARED,result.status());
        var attachment = result.value(); assertTrue(attachment.relativePath().startsWith("attachments/"));
        assertFalse(JsonCodec.write(attachment).contains(source.toString()));
        assertArrayEquals(Files.readAllBytes(source),Files.readAllBytes(root.resolve(attachment.relativePath())));
        AttachmentStore.verify(root,attachment);
        Files.write(root.resolve(attachment.relativePath()),new byte[]{1,2,3});
        try { AttachmentStore.verify(root,attachment); fail(); } catch (java.io.IOException expected) { assertNotNull(expected.getMessage()); }
    }
    @Test public void exactByteLimitAcceptedNextByteAndFourthImageRejected() throws Exception {
        Path image = image(); byte[] padded = Arrays.copyOf(Files.readAllBytes(image),5_000_000); Files.write(image,padded);
        AttachmentStore attachments = new AttachmentStore(root);
        assertEquals(Result.Status.PREPARED,attachments.importImage(image,"receipt",0).status());
        Files.write(image,Arrays.copyOf(padded,5_000_001));
        assertEquals(Result.Status.REJECTED,attachments.importImage(image,"receipt",0).status());
        assertEquals(Result.Status.REJECTED,attachments.importImage(image,"receipt",3).status());
    }
    @Test public void misleadingExtensionMissingSourceAndUnsafeReferencesRejected() throws Exception {
        Path source = temp.newFile("fake.png").toPath(); Files.writeString(source,"not an image");
        AttachmentStore attachments = new AttachmentStore(root);
        assertEquals(Result.Status.REJECTED,attachments.importImage(source,"receipt",0).status());
        assertEquals(Result.Status.FAILED,attachments.importImage(source.resolveSibling("missing.png"),"receipt",0).status());
        for (String invalid : List.of("../outside","attachments/../outside","/outside","C:/outside","\\\\server\\x","attachments\\x","attachments//x","./x","https://x")) {
            try { root.resolve(invalid); fail(invalid); } catch (java.io.IOException expected) { assertNotNull(expected.getMessage()); }
        }
    }
    @Test public void existingLinkOutsideRootIsRejectedWhenPlatformPermitsSymlinks() throws Exception {
        Path outside = temp.newFolder().toPath(); Path link = root.path().resolve("linked");
        try { Files.createSymbolicLink(link,outside); }
        catch (java.io.IOException | UnsupportedOperationException e) { org.junit.Assume.assumeNoException("Platform cannot create symbolic links",e); }
        try { root.resolve("linked/new.png"); fail(); } catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("escapes")); }
    }
}
