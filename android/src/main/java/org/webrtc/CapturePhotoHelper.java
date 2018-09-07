package org.webrtc;

import android.hardware.Camera;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;
import com.oney.WebRTCModule.WebRTCModule;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class CapturePhotoHelper {
    static final String TAG = "TakePictureModule";
    private CameraSession cameraSession = null;

    public CapturePhotoHelper(VideoCapturer videoCapturer) throws Exception {
        try {
            cameraSession = getCameraSessionInstance(videoCapturer);
        } catch (Exception e) {
            String message = "Error getting camera session instance";
            Log.e(TAG, message, e);
            throw new Exception(message);
        }
    }

    public void capturePhoto(ReactApplicationContext context,
                             final ReadableMap options,
                             final Callback successCallback,
                             final Callback errorCallback) {
        if (cameraSession instanceof Camera2Session) {
            ((Camera2Session) cameraSession).takePhoto(context, options, successCallback, errorCallback);
        } else {
            this.takePicture(context, options, successCallback, errorCallback);
        }
    }

    public void switchFlash(final ReadableMap options,
                            final Callback successCallback,
                            final Callback errorCallback) {
        if (cameraSession instanceof Camera2Session) {
            ((Camera2Session) cameraSession).switchFlash(options, successCallback, errorCallback);
        } else {
            this.switchFlashCamera1(options, successCallback, errorCallback);
        }
    }

    @SuppressWarnings("deprecation")
    private CameraSession getCameraSessionInstance(VideoCapturer videoCapturer) throws Exception {
        CameraSession cameraSession = null;

        if (videoCapturer instanceof CameraCapturer) {
            Field cameraSessionField = CameraCapturer.class.getDeclaredField("currentSession");
            cameraSessionField.setAccessible(true);

            cameraSession = (CameraSession) cameraSessionField.get(videoCapturer);
        }

        if (cameraSession == null) {
            throw new Exception("Could not get camera session instance");
        }

        return cameraSession;
    }

    @SuppressWarnings("deprecation")
    private void switchFlashCamera1(final ReadableMap options,
                                    final Callback successCallback,
                                    final Callback errorCallback) {
        Camera camera;
        try {
            camera = getCameraInstance(cameraSession);
        } catch (Exception e) {
            String message = "Error getting camera instance for stream";
            Log.d(TAG, message, e);
            errorCallback.invoke(message);
            return;
        }

        final int flashMode = options.getInt("flashMode");

        try {

            Camera.Parameters p = camera.getParameters();

            if (flashMode == 1) {
                p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            } else {
                p.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            }

            camera.setParameters(p);
        } catch (Exception e) {
            Log.e(TAG, "Error switching flash");
            errorCallback.invoke(e.getMessage());
            return;
        }

        successCallback.invoke("Successful");
    }


    @SuppressWarnings("deprecation")
    private void takePicture(ReactApplicationContext context,
                             final ReadableMap options,
                             final Callback successCallback,
                             final Callback errorCallback) {

        CapturePhoto capturePhoto = new CapturePhoto();
        capturePhoto.setContext(context);
        capturePhoto.setOptionsAndCallback(options, successCallback, errorCallback);

        final int captureTarget = options.getInt("captureTarget");

        Camera camera;
        try {
            camera = getCameraInstance(cameraSession);
        } catch (Exception e) {
            String message = "Error getting camera instance for stream";
            Log.d(TAG, message, e);
            errorCallback.invoke(message);
            return;
        }

        int orientation = -1;
        try {
            orientation = getFrameOrientation(cameraSession);
        } catch (Exception e) {
            Log.d(TAG, "Error getting frame orientation for stream", e);
        }

        capturePhoto.setOrientation(orientation);

        camera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(final byte[] jpeg, final Camera camera) {
                camera.startPreview();
                Runnable savePhotoRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (captureTarget == WebRTCModule.RCT_CAMERA_CAPTURE_TARGET_MEMORY) {
                            String encoded = Base64.encodeToString(jpeg, Base64.DEFAULT);
                            successCallback.invoke(encoded);
                        } else {
                            try {
                                String path = capturePhoto.savePicture(jpeg);
                                successCallback.invoke(path);
                            } catch (Exception e) {
                                String message = "Error saving picture";
                                Log.d(TAG, message, e);
                                errorCallback.invoke(message);
                            }
                        }
                    }
                };
                AsyncTask.execute(savePhotoRunnable);
            }
        });
    }

    @SuppressWarnings("deprecation")
    private Camera getCameraInstance(CameraSession cameraSession) throws Exception {
        Camera camera = null;

        Field cameraField = cameraSession.getClass().getDeclaredField("camera");
        cameraField.setAccessible(true);

        camera = (Camera) cameraField.get(cameraSession);

        if (camera == null) {
            throw new Exception("Could not get camera instance");
        }

        return camera;
    }

    private int getFrameOrientation(CameraSession cameraSession) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method getFrameOrientation = cameraSession.getClass().getDeclaredMethod("getFrameOrientation");
        getFrameOrientation.setAccessible(true);
        return (Integer) getFrameOrientation.invoke(cameraSession);
    }
}
