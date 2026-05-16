package com.phynix.artham.utils;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Utility class to apply a real blur effect on the screen behind dialogs.
 * Captures the activity's content as a bitmap, blurs it, and overlays it
 * behind the dialog for a premium frosted-glass effect.
 *
 * Usage:
 *   Dialog dialog = new Dialog(context);
 *   dialog.setContentView(R.layout.my_dialog);
 *   DialogUtils.applyBlurEffect(dialog, activity);
 *   dialog.show();
 */
public class DialogUtils {

    private static final float DIM_AMOUNT = 0.3f;
    private static final int BLUR_RADIUS = 25;
    private static final float SCALE_FACTOR = 0.25f; // Downscale for faster blur

    /**
     * Applies a blur effect by capturing the activity screen, blurring it,
     * and placing it as an overlay behind the dialog.
     */
    public static void applyBlurEffect(Dialog dialog, Activity activity) {
        if (dialog == null || activity == null) return;

        Window window = dialog.getWindow();
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(DIM_AMOUNT);
        }

        // Capture and blur the activity screen
        View rootView = activity.getWindow().getDecorView().getRootView();
        ImageView blurOverlay = createBlurOverlay(activity, rootView);

        if (blurOverlay != null) {
            // Add the blur overlay to the activity's root
            ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            );
            decorView.addView(blurOverlay, params);

            // Remove overlay when dialog dismisses
            dialog.setOnDismissListener(d -> {
                decorView.removeView(blurOverlay);
            });
        }
    }

    /**
     * Creates an ImageView with the blurred screenshot of the given view.
     */
    private static ImageView createBlurOverlay(Activity activity, View view) {
        try {
            // Capture the screen as a scaled-down bitmap
            int width = view.getWidth();
            int height = view.getHeight();
            if (width <= 0 || height <= 0) return null;

            int scaledWidth = Math.max(1, (int) (width * SCALE_FACTOR));
            int scaledHeight = Math.max(1, (int) (height * SCALE_FACTOR));

            Bitmap original = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(original);
            canvas.scale(SCALE_FACTOR, SCALE_FACTOR);
            view.draw(canvas);

            // Apply stack blur
            Bitmap blurred = stackBlur(original, BLUR_RADIUS);

            ImageView imageView = new ImageView(activity);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setImageBitmap(blurred);

            // Use RenderEffect for extra smoothness on API 31+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                imageView.setRenderEffect(
                        RenderEffect.createBlurEffect(10f, 10f, Shader.TileMode.CLAMP)
                );
            }

            return imageView;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fast stack blur algorithm. Works on all API levels without any dependencies.
     * Based on Mario Klingemann's stack blur algorithm.
     */
    private static Bitmap stackBlur(Bitmap source, int radius) {
        if (radius < 1) return source;

        Bitmap bitmap = source.copy(source.getConfig(), true);
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        int[] pix = new int[w * h];
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);

        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = radius + radius + 1;

        int[] r = new int[wh];
        int[] g = new int[wh];
        int[] b = new int[wh];
        int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
        int[] vmin = new int[Math.max(w, h)];

        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int[] dv = new int[256 * divsum];
        for (i = 0; i < 256 * divsum; i++) {
            dv[i] = (i / divsum);
        }

        yw = yi = 0;

        int[][] stack = new int[div][3];
        int stackpointer;
        int stackstart;
        int[] sir;
        int rbs;
        int r1 = radius + 1;
        int routsum, goutsum, boutsum;
        int rinsum, ginsum, binsum;

        for (y = 0; y < h; y++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            for (i = -radius; i <= radius; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                sir = stack[i + radius];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) {
                    rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2];
                } else {
                    routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2];
                }
            }
            stackpointer = radius;

            for (x = 0; x < w; x++) {
                r[yi] = dv[rsum]; g[yi] = dv[gsum]; b[yi] = dv[bsum];
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum;
                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2];
                if (y == 0) vmin[x] = Math.min(x + radius + 1, wm);
                p = pix[yw + vmin[x]];
                sir[0] = (p & 0xff0000) >> 16; sir[1] = (p & 0x00ff00) >> 8; sir[2] = (p & 0x0000ff);
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2];
                rsum += rinsum; gsum += ginsum; bsum += binsum;
                stackpointer = (stackpointer + 1) % div;
                sir = stack[(stackpointer) % div];
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2];
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2];
                yi++;
            }
            yw += w;
        }

        for (x = 0; x < w; x++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            yp = -radius * w;
            for (i = -radius; i <= radius; i++) {
                yi = Math.max(0, yp) + x;
                sir = stack[i + radius];
                sir[0] = r[yi]; sir[1] = g[yi]; sir[2] = b[yi];
                rbs = r1 - Math.abs(i);
                rsum += r[yi] * rbs; gsum += g[yi] * rbs; bsum += b[yi] * rbs;
                if (i > 0) {
                    rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2];
                } else {
                    routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2];
                }
                if (i < hm) yp += w;
            }
            yi = x;
            stackpointer = radius;
            for (y = 0; y < h; y++) {
                pix[yi] = (0xff000000 & pix[yi]) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum;
                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2];
                if (x == 0) vmin[y] = Math.min(y + r1, hm) * w;
                p = x + vmin[y];
                sir[0] = r[p]; sir[1] = g[p]; sir[2] = b[p];
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2];
                rsum += rinsum; gsum += ginsum; bsum += binsum;
                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer % div];
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2];
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2];
                yi += w;
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h);
        return bitmap;
    }
}
