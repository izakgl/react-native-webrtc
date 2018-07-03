package org.webrtc;

import android.util.Log;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;

import java.lang.reflect.Field;

public class CapturePhotoHelper {
    static final String TAG = "TakePictureModule";
    private CameraSession cameraSession = null;

    public CapturePhotoHelper(VideoCapturer videoCapturer) throws Exception {
        try {
            cameraSession = getCameraSessionInstance(videoCapturer);
        } catch(Exception e) {
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
        }
    }

    public void switchFlash(final ReadableMap options,
                            final Callback successCallback,
                            final Callback errorCallback) {
        if (cameraSession instanceof Camera2Session) {
            ((Camera2Session) cameraSession).switchFlash(options, successCallback, errorCallback);
        }
    }

    @SuppressWarnings("deprecation")
    private CameraSession getCameraSessionInstance(VideoCapturer videoCapturer) throws Exception {
        CameraSession cameraSession = null;

        if(videoCapturer instanceof CameraCapturer) {
            Field cameraSessionField = CameraCapturer.class.getDeclaredField("currentSession");
            cameraSessionField.setAccessible(true);

            cameraSession = (CameraSession) cameraSessionField.get(videoCapturer);
        }

        if (cameraSession == null) {
            throw new Exception("Could not get camera session instance");
        }

        return cameraSession;
    }
}
