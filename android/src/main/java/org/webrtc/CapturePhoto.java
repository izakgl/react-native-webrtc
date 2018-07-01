package org.webrtc;

import android.annotation.TargetApi;
import android.media.Image;
import android.media.ImageReader;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

@TargetApi(21)
public class CapturePhoto {
    private static final String TAG = "Camera2Session";
    private int orientation;
    private ReactApplicationContext context = null;
    private Callback successCallback;
    private Callback errorCallback;
    private int maxSize = 2000;
    private double maxJpegQuality = 1.0;

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
        this.successCallback = successCallback;
        this.errorCallback = errorCallback;
    }

    public final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            Image image = imageReader.acquireLatestImage();
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            try {
                String path = savePicture(bytes);
                successCallback.invoke(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
            image.close();
        }
    };

    private synchronized String savePicture(byte[] jpeg) throws IOException {
        String filename = UUID.randomUUID().toString();
        File file = null;

        file = getOutputCameraRollFile(filename);
        writePictureToFile(jpeg, file, maxSize, maxJpegQuality, orientation);
        addToMediaStore(file.getAbsolutePath());

        return Uri.fromFile(file).toString();
    }

    private String writePictureToFile(byte[] jpeg, File file, int maxSize, double jpegQuality, int orientation) throws IOException {
        FileOutputStream output = new FileOutputStream(file);
        output.write(jpeg);
        output.close();

        Matrix matrix = new Matrix();
        Log.d(TAG, "orientation " + orientation);
        if (orientation != 0) {
            matrix.postRotate(orientation);
        }

        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());

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

        int compression = (int) (100 * jpegQuality);
        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
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
