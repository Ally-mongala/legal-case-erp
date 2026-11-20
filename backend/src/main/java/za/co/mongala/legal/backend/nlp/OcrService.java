package za.co.mongala.legal.backend.nlp;

import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Optical character recognition for scanned, image-only PDF pages (FR1 gap closed).
 *
 * A page with no text layer is rendered to a 300 dpi greyscale image with PDFBox and
 * read by the Tesseract command-line engine. Tesseract is an external program, so it
 * is optional: when it is not installed the pipeline keeps the old, explicit
 * "scanned image - OCR not available" failure instead of guessing.
 *
 * Configuration:
 *   LCE_TESSERACT   path or name of the tesseract executable (default "tesseract")
 *   LCE_OCR_LANG    Tesseract language pack (default "eng")
 */
public final class OcrService {

    private static final String BINARY = envOr("LCE_TESSERACT", "tesseract");
    private static final String LANG = envOr("LCE_OCR_LANG", "eng");
    private static final float DPI = 300f;

    private static volatile Boolean available;

    private OcrService() {}

    /** True when the tesseract executable can be started. Checked once and cached. */
    public static boolean isAvailable() {
        if (available == null) {
            synchronized (OcrService.class) {
                if (available == null) {
                    available = probe();
                }
            }
        }
        return available;
    }

    /** Renders one page (zero-based index) and returns the recognised text, or "" on failure. */
    public static String recognisePage(PDFRenderer renderer, int pageIndex) {
        Path image = null;
        try {
            BufferedImage page = renderer.renderImageWithDPI(pageIndex, DPI, ImageType.GRAY);
            image = Files.createTempFile("lce-ocr-", ".png");
            ImageIO.write(page, "png", image.toFile());

            Process p = new ProcessBuilder(BINARY, image.toString(), "stdout",
                    "-l", LANG, "--psm", "6")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(120, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "";
            }
            return p.exitValue() == 0 ? text.strip() : "";
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return "";
        } finally {
            if (image != null) {
                try {
                    Files.deleteIfExists(image);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static boolean probe() {
        try {
            Process p = new ProcessBuilder(BINARY, "--version")
                    .redirectErrorStream(true)
                    .start();
            p.getInputStream().readAllBytes();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String envOr(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
