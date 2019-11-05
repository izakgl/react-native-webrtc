package org.webrtc;

import android.annotation.TargetApi;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;
import com.oney.WebRTCModule.WebRTCModule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

public class CapturePhoto {
    private static final String TAG = "Camera2Session";
    private int orientation;
    private ReactApplicationContext context = null;
    private Callback successCallback;
    private Callback errorCallback;
    private int maxSize = 2000;
    private double maxJpegQuality = 1.0;
    private int captureTarget = WebRTCModule.RCT_CAMERA_CAPTURE_TARGET_CAMERA_ROLL;

    public void setContext(ReactApplicationContext context) {
        this.context = context;
    }

    public void setOrientation(int orientation) {
        this.orientation = orientation;
    }

    public void setOptionsAndCallback(final ReadableMap options,
                                      final Callback successCallback,
                                      final Callback errorCallback) {
        this.maxJpegQuality = options.getDouble("maxJpegQuality");
        this.maxSize = options.getInt("maxSize");
        this.captureTarget = options.getInt("captureTarget");
        this.successCallback = successCallback;
        this.errorCallback = errorCallback;
    }

    @TargetApi(21)
    public final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            Runnable savePhotoRunnable = new Runnable() {
                @Override
                public void run() {
                    Image image = imageReader.acquireLatestImage();
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);

                    if (captureTarget == WebRTCModule.RCT_CAMERA_CAPTURE_TARGET_MEMORY) {
                        String encoded = Base64.encodeToString(bytes, Base64.DEFAULT);
                        successCallback.invoke(encoded);
                    } else {
                        try {
                            String path = savePicture(bytes, captureTarget);
                            successCallback.invoke(path);
                        } catch (Exception e) {
                            e.printStackTrace();
                            errorCallback.invoke(e.getMessage());
                        }
                    }
                    image.close();
                }
            };
            AsyncTask.execute(savePhotoRunnable);
        }
    };

    public synchronized String savePicture(byte[] jpeg, int target) throws Exception {
        String filename = UUID.randomUUID().toString();
        File file = null;

        if (target == WebRTCModule.RCT_CAMERA_CAPTURE_TARGET_CAMERA_ROLL) {
            file = getOutputCameraRollFile(filename);
        } else if (target == WebRTCModule.RCT_CAMERA_CAPTURE_TARGET_TEMP) {
            file = getOutputCacheFile(filename);
        }

        writePictureToFile(jpeg, file, maxSize, maxJpegQuality, orientation);

        if (target == WebRTCModule.RCT_CAMERA_CAPTURE_TARGET_CAMERA_ROLL) {
            addToMediaStore(file.getAbsolutePath());
        }

        return Uri.fromFile(file).toString();
    }

    private String writePictureToFile(byte[] jpeg, File file, int maxSize, double jpegQuality, int orientation) throws Exception {
        FileOutputStream output = new FileOutputStream(file);
        output.write(jpeg);
        output.close();

        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());

        int rotation = ExifInterface.ORIENTATION_NORMAL;

        try {
            if (!file.getAbsolutePath().equals("")) {
                ExifInterface exif = new ExifInterface(file.getAbsolutePath());
                rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exif data error: " + e.getMessage());
        }

        Matrix matrix = new Matrix();
        Log.d(TAG, "orientation " + orientation);
        if (orientation != 0 && rotation != ExifInterface.ORIENTATION_UNDEFINED) {
            matrix.postRotate(orientation);
        }

        // scale if needed
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // only resize if image larger than maxSize
        if (width > maxSize && width > maxSize) {
            Rect originalRect = new Rect(0, 0, width, height);
            Rect scaledRect = scaleDimension(originalRect, maxSize);

            Log.d(TAG, "scaled width = " + scaledRect.width() + ", scaled height = " + scaledRect.height());

            // calculate the scale
            float scaleWidth = ((float) scaledRect.width()) / width;
            float scaleHeight = ((float) scaledRect.height()) / height;

            matrix.postScale(scaleWidth, scaleHeight);
        }

        FileOutputStream finalOutput = new FileOutputStream(file, false);

        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        int compression = (int) (100 * jpegQuality);
        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, compression, finalOutput);

        finalOutput.close();

        return file.getAbsolutePath();
    }

    private void addToMediaStore(String path) {
        MediaScannerConnection.scanFile(this.context, new String[]{path}, null, null);
    }

    private File getOutputCameraRollFile(String fileName) {
        return getOutputFile(
                fileName + ".jpeg",
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        );
    }

    private File getOutputCacheFile(String fileName) {
        return getOutputFile(
                fileName + ".jpeg",
                context.getCacheDir()
        );
    }


    private File getOutputFile(String fileName, File storageDir) {
        // Create the storage directory if it does not exist
        if (!storageDir.exists()) {
            if (!storageDir.mkdirs()) {
                Log.e(TAG, "failed to create directory:" + storageDir.getAbsolutePath());
                return null;
            }
        }

        return new File(String.format("%s%s%s", storageDir.getPath(), File.separator, fileName));
    }

    private static Rect scaleDimension(Rect originalRect, int maxSize) {

        int originalWidth = originalRect.width();
        int originalHeight = originalRect.height();
        int newWidth = originalWidth;
        int newHeight = originalHeight;

        // first check if we need to scale width
        if (originalWidth > maxSize) {
            //scale width to fit
            newWidth = maxSize;
            //scale height to maintain aspect ratio
            newHeight = (newWidth * originalHeight) / originalWidth;
        }

        // then check if we need to scale even with the new height
        if (newHeight > maxSize) {
            //scale height to fit instead
            newHeight = maxSize;
            //scale width to maintain aspect ratio
            newWidth = (newHeight * originalWidth) / originalHeight;
        }

        return new Rect(0, 0, newWidth, newHeight);
    }
}
