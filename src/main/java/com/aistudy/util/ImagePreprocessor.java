package com.aistudy.util;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 送视觉模型前的图片标准化：纠正手机照片方向、把长边控制在可读范围、超长图切块。
 * <p>小图直接送模型会丢掉公式的上下标与分式，这里统一放大到长边 1600px；
 * 过大的图缩到 2400px 以内，避免视觉模型把图像降采样成更低分辨率。</p>
 */
public final class ImagePreprocessor {

    /** 视觉模型能读清公式的最小长边 */
    private static final int MIN_EDGE = 1600;
    /** 超过这个长边就缩小，减少无关像素 */
    private static final int MAX_EDGE = 2400;
    /** 超过该高度视为长图，按条带切块识别 */
    private static final int TALL_HEIGHT = 2200;
    private static final int BAND_HEIGHT = 1400;
    private static final int BAND_OVERLAP = 120;
    private static final int MAX_BANDS = 4;
    private static final float JPEG_QUALITY = 0.92f;

    private ImagePreprocessor() {
    }

    /**
     * @param raw 原始图片字节（jpg/png/gif 等）
     * @return 待识别的图片列表；普通图片只有一张，长图按条带切分
     */
    public static List<byte[]> prepare(byte[] raw) {
        List<byte[]> out = new ArrayList<>();
        if (raw == null || raw.length == 0) {
            return out;
        }
        BufferedImage img = read(raw);
        if (img == null) {
            // ImageIO 不支持的格式（如 webp）保持原样，交给模型自己解码
            out.add(raw);
            return out;
        }
        img = orient(img, readExifOrientation(raw));
        img = scale(img);
        if (img.getHeight() > TALL_HEIGHT) {
            for (BufferedImage band : bands(img)) {
                byte[] jpeg = jpeg(band);
                if (jpeg != null) {
                    out.add(jpeg);
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        byte[] jpeg = jpeg(img);
        out.add(jpeg == null ? raw : jpeg);
        return out;
    }

    private static BufferedImage read(byte[] raw) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(raw));
            if (img == null) {
                return null;
            }
            // 统一成 RGB，避免透明通道干扰 JPEG 编码
            if (img.getType() != BufferedImage.TYPE_INT_RGB) {
                BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(),
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                g.drawImage(img, 0, 0, null);
                g.dispose();
                return rgb;
            }
            return img;
        } catch (Exception e) {
            return null;
        }
    }

    /** 按长边归一化：小图放大（最多 3 倍），大图缩小 */
    private static BufferedImage scale(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int longEdge = Math.max(w, h);
        double factor = 1.0;
        if (longEdge < MIN_EDGE) {
            factor = Math.min(MIN_EDGE / (double) longEdge, 3.0);
        } else if (longEdge > MAX_EDGE) {
            factor = MAX_EDGE / (double) longEdge;
        }
        if (Math.abs(factor - 1.0) < 0.01) {
            return img;
        }
        int nw = Math.max(1, (int) Math.round(w * factor));
        int nh = Math.max(1, (int) Math.round(h * factor));
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(img, 0, 0, nw, nh, null);
        g.dispose();
        return dst;
    }

    private static List<BufferedImage> bands(BufferedImage img) {
        List<BufferedImage> list = new ArrayList<>();
        int h = img.getHeight();
        int step = BAND_HEIGHT - BAND_OVERLAP;
        for (int y = 0; y < h && list.size() < MAX_BANDS; y += step) {
            int height = Math.min(BAND_HEIGHT, h - y);
            list.add(img.getSubimage(0, y, img.getWidth(), height));
            if (y + height >= h) {
                break;
            }
        }
        return list;
    }

    private static byte[] jpeg(BufferedImage img) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                return null;
            }
            ImageWriter writer = writers.next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(img, null, null), param);
            } finally {
                writer.dispose();
            }
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------- EXIF 方向 ----------------

    /** 读取 JPEG 的 EXIF Orientation（1-8），读取失败按 1 处理 */
    static int readExifOrientation(byte[] jpeg) {
        if (jpeg == null || jpeg.length < 4
                || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
            return 1;
        }
        int pos = 2;
        while (pos + 4 <= jpeg.length) {
            if ((jpeg[pos] & 0xFF) != 0xFF) {
                pos++;
                continue;
            }
            int marker = jpeg[pos + 1] & 0xFF;
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                pos += 2;
                continue;
            }
            if (marker == 0xDA || marker == 0xD9) {
                break;
            }
            int len = ((jpeg[pos + 2] & 0xFF) << 8) | (jpeg[pos + 3] & 0xFF);
            if (len < 2) {
                break;
            }
            int dataStart = pos + 4;
            if (marker == 0xE1 && len >= 8 && hasExifHeader(jpeg, dataStart)) {
                return tiffOrientation(jpeg, dataStart + 6, Math.min(jpeg.length, dataStart + len - 2));
            }
            pos += 2 + len;
        }
        return 1;
    }

    private static boolean hasExifHeader(byte[] b, int start) {
        if (start + 6 > b.length) {
            return false;
        }
        return b[start] == 'E' && b[start + 1] == 'x' && b[start + 2] == 'i'
                && b[start + 3] == 'f' && b[start + 4] == 0 && b[start + 5] == 0;
    }

    private static int tiffOrientation(byte[] b, int start, int end) {
        if (start + 8 > end) {
            return 1;
        }
        boolean little = b[start] == 'I' && b[start + 1] == 'I';
        boolean big = b[start] == 'M' && b[start + 1] == 'M';
        if (!little && !big) {
            return 1;
        }
        int ifd = start + readInt(b, start + 4, little);
        if (ifd + 2 > end) {
            return 1;
        }
        int count = readShort(b, ifd, little);
        int p = ifd + 2;
        for (int i = 0; i < count; i++) {
            if (p + 12 > end) {
                break;
            }
            int tag = readShort(b, p, little);
            int type = readShort(b, p + 2, little);
            long n = readInt(b, p + 4, little) & 0xFFFFFFFFL;
            if (tag == 0x0112 && type == 3 && n >= 1) {
                int v = readShort(b, p + 8, little);
                return (v >= 1 && v <= 8) ? v : 1;
            }
            p += 12;
        }
        return 1;
    }

    private static int readShort(byte[] b, int p, boolean little) {
        if (p + 2 > b.length) {
            return 0;
        }
        int a = b[p] & 0xFF;
        int c = b[p + 1] & 0xFF;
        return little ? (c << 8) | a : (a << 8) | c;
    }

    private static int readInt(byte[] b, int p, boolean little) {
        if (p + 4 > b.length) {
            return 0;
        }
        int a = b[p] & 0xFF;
        int c = b[p + 1] & 0xFF;
        int d = b[p + 2] & 0xFF;
        int e = b[p + 3] & 0xFF;
        return little ? (e << 24) | (d << 16) | (c << 8) | a : (a << 24) | (c << 16) | (d << 8) | e;
    }

    /** 按 EXIF 方向把图片转正 */
    private static BufferedImage orient(BufferedImage img, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return img;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        boolean swap = orientation >= 5;
        int nw = swap ? h : w;
        int nh = swap ? w : h;
        AffineTransform t;
        switch (orientation) {
            case 2:
                t = new AffineTransform(-1, 0, 0, 1, w, 0);
                break;
            case 3:
                t = new AffineTransform(-1, 0, 0, -1, w, h);
                break;
            case 4:
                t = new AffineTransform(1, 0, 0, -1, 0, h);
                break;
            case 5:
                t = new AffineTransform(0, 1, 1, 0, 0, 0);
                break;
            case 6:
                t = new AffineTransform(0, 1, -1, 0, h, 0);
                break;
            case 7:
                t = new AffineTransform(0, -1, -1, 0, h, w);
                break;
            case 8:
                t = new AffineTransform(0, -1, 1, 0, 0, w);
                break;
            default:
                return img;
        }
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, nw, nh);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(img, t, null);
        g.dispose();
        return dst;
    }
}
