package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.remote;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.EnumMap;
import java.util.Map;

/** Locally generated QR codes; never uses a third-party QR image service. */
public final class AiSubtitleQrCode {
    private AiSubtitleQrCode() {
    }

    public static Bitmap create(String content, int size) {
        if (content == null || content.trim().isEmpty() || size <= 0) {
            return null;
        }

        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, Integer.valueOf(1));
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE,
                    size, size, hints);
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            int[] pixels = new int[width * height];

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y * width + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (WriterException | RuntimeException e) {
            return null;
        }
    }
}
