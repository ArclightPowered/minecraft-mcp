package io.izzel.minecraftmcp.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public final class Images {
    private Images() {}

    /**
     * The PNG at {@code file} as base64, scaled down to {@code maxWidth} when wider (0 keeps the full size). Scaling
     * needs java.desktop; where the game's module layer cannot reach it, the full-size file is returned instead.
     */
    public static String pngBase64(Path file, int maxWidth) throws IOException {
        byte[] png = Files.readAllBytes(file);
        if (maxWidth > 0) {
            try {
                png = Scaler.scale(png, maxWidth);
            } catch (LinkageError | RuntimeException e) {
                // keep the original
            }
        }
        return Base64.getEncoder().encodeToString(png);
    }

    /** Loaded only when scaling is attempted, so a missing java.desktop fails here and not in Images. */
    private static final class Scaler {
        static byte[] scale(byte[] png, int maxWidth) throws IOException {
            var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
            if (image == null || image.getWidth() <= maxWidth) return png;
            int height = Math.max(1, image.getHeight() * maxWidth / image.getWidth());
            var scaled = new java.awt.image.BufferedImage(maxWidth, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
            var graphics = scaled.createGraphics();
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(image, 0, 0, maxWidth, height, null);
            graphics.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(scaled, "png", out);
            return out.toByteArray();
        }
    }
}
