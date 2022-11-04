package ee.ims.cayenne;

import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.aldebaran.qi.sdk.QiContext;
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks;
import com.aldebaran.qi.sdk.builder.AnimateBuilder;
import com.aldebaran.qi.sdk.builder.AnimationBuilder;
import com.aldebaran.qi.sdk.object.actuation.Animate;
import com.aldebaran.qi.sdk.object.actuation.Animation;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Executor;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;

public class CommunicationService extends Service implements RobotLifecycleCallbacks {
    private String TAG = "CommunicationService";
    private final IBinder binder = new LocalBinder();
    private Executor executor;
    private WebSocket webSocket;
    private Boolean isWebSocketConnected = false;
    private int connectionEstablishingDelayMillis = 1000;
    private PepperTaskListener taskListener;
    private Callbacks activity;
    private QiContext qiContext;
    private String serverURL;
    private BroadcastReceiver websocketMessageReceiver;
    private BroadcastReceiver websocketUpReceiver;
    private BroadcastReceiver websocketDownReceiver;
    private MediaPlayer mediaPlayer;

    // Delay implementation from https://stackoverflow.com/a/3039718
    private static class DelayHandler extends Handler {}
    private final DelayHandler delayHandler = new DelayHandler();

    public class LocalBinder extends Binder {
        CommunicationService getService(Executor executor) {
            CommunicationService.this.executor = executor;
            return CommunicationService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setServerURL(String url) {
        serverURL = url;
    }

    public void startListening(String serverURL) {
        if (webSocket != null) {
            webSocket.close(1000, null);
        }

        Request request;
        if (serverURL.isEmpty()) {
            request = new Request.Builder().url("ws://10.0.2.2:8080/api/pepper/initiate").build();
        } else {
            request = new Request.Builder().url(serverURL).build();
        }
        Log.i(TAG, request.toString());
        OkHttpClient client = new OkHttpClient();
        webSocket = client.newWebSocket(request, taskListener);
        client.dispatcher().executorService().shutdown();
        isWebSocketConnected = true;

        Log.d(TAG, String.format("Connection to %s has been established", serverURL));
    }

    // service lifecycle control

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(websocketMessageReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(websocketUpReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(websocketDownReceiver);
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    // pepper callbacks

    @Override
    public void onRobotFocusGained(QiContext qiContext) { // it runs on the background thread
        Log.i(TAG, "focus gained");
        if (!isWebSocketConnected) {
            this.qiContext = qiContext;
            taskListener = new PepperTaskListener(qiContext);

            websocketMessageReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    executor.execute(() -> {
                        // clear image view for any new task
//                         if (isNewSayTask || isNewMoveTask || isNewShowImageTask || isNewShowURLTask) {
//                            activity.setImageResource(android.R.color.transparent);
//                        }
                        if (taskListener.getNewAuthTaskAvailable()) {
                            activity.auth(taskListener.getCurrentAuthTask().id, taskListener.getCurrentAuthTask().content);
                            taskListener.setNewAuthTaskAvailable(false);
                        }
                        if (taskListener.getNewSayTaskAvailable()) {
                            say(taskListener.getCurrentSayTask());
                        }
                        if (taskListener.getNewMoveTaskAvailable()) {
                            move(taskListener.getCurrentMoveTask());
                        }
                        if (taskListener.getNewShowImageTaskAvailable()) {
                            showImage(taskListener.getCurrentShowImageTask());
                        }
                        if (taskListener.getNewShowURLTaskAvailable()) {
                            showURL(taskListener.getCurrentShowURLTask());
                        }
                        // Clearing images / the screen
                        if (taskListener.getNewClearImageTaskAvailable()) {
                            activity.setImageResource(android.R.color.transparent);
                            taskListener.setNewClearImageTaskAvailable(false);
                        }
                        // Clearing fragments (authentication/Youtube)
                        if (taskListener.getNewClearFragmentTaskAvailable()) {
                            activity.clearFragment();
                            taskListener.setNewClearFragmentTaskAvailable(false);
                        }
                    });
                }
            };
            websocketUpReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    executor.execute(() -> activity.setConnectionStatus(true));
                }
            };
            websocketDownReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    executor.execute(() -> {
                        isWebSocketConnected = false;
                        activity.setConnectionStatus(false);
                        while (!isWebSocketConnected) {
                            try {
                                Thread.sleep(connectionEstablishingDelayMillis);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            startListening(serverURL);
                        }
                    });
                }
            };

            LocalBroadcastManager.getInstance(qiContext).registerReceiver(websocketMessageReceiver, new IntentFilter(PepperTaskListener.BROADCAST_WEBSOCKET_MESSAGE_KEY));
            LocalBroadcastManager.getInstance(qiContext).registerReceiver(websocketUpReceiver, new IntentFilter(PepperTaskListener.BROADCAST_WEBSOCKET_UP_KEY));
            LocalBroadcastManager.getInstance(qiContext).registerReceiver(websocketDownReceiver, new IntentFilter(PepperTaskListener.BROADCAST_WEBSOCKET_DOWN_KEY));

            startListening(serverURL);
        }
    }

    @Override
    public void onRobotFocusLost() {
        Log.i(TAG, "focus lost");
        this.qiContext = null;
    }

    @Override
    public void onRobotFocusRefused(String reason) {
        Log.i(TAG, "focus refused");
    }

    // helpers

    public void move(PepperTask currentMoveTask) {
        if (currentMoveTask == null) {
            return;
        }

        Animation motion = null;
        if (currentMoveTask.content.length() == 0) {
            Log.d(TAG, "empty content");
            int resID = getResources().getIdentifier(currentMoveTask.name, "raw", getPackageName());
            if (resID != 0) {
                motion = AnimationBuilder.with(qiContext).withResources(resID).build();
            } else {
                Log.d(TAG, String.format("can't find resource %s", currentMoveTask.name));
            }
        } else {
            motion = AnimationBuilder.with(qiContext).withTexts(currentMoveTask.content).build();
        }
        if (motion != null) {
            Animate move = AnimateBuilder.with(qiContext).withAnimation(motion).build();
            delayHandler.postDelayed(() -> move.async().run().thenConsume(voidFuture -> {
                if (voidFuture.isSuccess()) {
                    Log.d(TAG, "Animation has finished");
                    webSocket.send(String.format("{\"action_success\": \"%s\"}", currentMoveTask.id));
                } else if (voidFuture.hasError()) {
                    Log.d(TAG, "Animation had an error:", voidFuture.getError());
                    webSocket.send(String.format("{\"action_error\": \"%s\"}", currentMoveTask.id));
                }
                }), currentMoveTask.delay*1000);
        }

        taskListener.setNewMoveTaskAvailable(false);
    }

    public void say(PepperTask currentSayTask) {
        // Play the audio via MediaPlayer in lieu of using Say from PepperSDK
        try {
            URI uri = new URI(serverURL);
            String src = String.format("http://%s:%s/%s", uri.getHost(), uri.getPort(), currentSayTask.content);
            Log.i(TAG, String.format("Processing %s", src));
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
            );
            mediaPlayer.setDataSource(src);
            mediaPlayer.prepare();
            mediaPlayer.setOnCompletionListener(mp -> {
                Log.i(TAG, String.format("Say: Playback of %s has finished!", currentSayTask.name));
                mediaPlayer.release();
                mediaPlayer = null;
                webSocket.send(String.format("{\"action_success\": \"%s\"}", currentSayTask.id));
            });
            delayHandler.postDelayed(() -> mediaPlayer.start(), currentSayTask.delay*1000);
        }
        catch (IOException e) {
            Log.e(TAG, String.format("Say ran into an IOException: %s", e));
            webSocket.send(String.format("{\"action_error\": \"%s\"}", currentSayTask.id));
        }
        catch (URISyntaxException e) {
            Log.e(TAG, String.format("Say ran into an URISyntaxException: %s", e));
            webSocket.send(String.format("{\"action_error\": \"%s\"}", currentSayTask.id));
        }
        taskListener.setNewSayTaskAvailable(false);
    }

    public static class ImageDelayer implements Runnable {
        private final PepperTask rShowImageTask;
        private final Callbacks rActivity;
        private final WebSocket rWebSocket;
        private final PepperTaskListener rTaskListener;

        public ImageDelayer(PepperTask showImageTask,
                            Callbacks activity,
                            WebSocket webSocket,
                            PepperTaskListener taskListener) {
            rShowImageTask = showImageTask;
            rActivity = activity;
            rWebSocket = webSocket;
            rTaskListener = taskListener;
        }

        @Override
        public void run() {
            byte[] imageContent = rShowImageTask.byteContent;
            Bitmap image = BitmapFactory.decodeByteArray(imageContent, 0, imageContent.length);
            rActivity.setImage((image));
            rWebSocket.send(String.format("{\"action_success\": \"%s\"}", rShowImageTask.id));
            rTaskListener.setNewShowImageTaskAvailable(false);
        }
    }

    public void showImage(PepperTask currentShowImageTask) {
        ImageDelayer imageDelayer = new ImageDelayer(currentShowImageTask, activity, webSocket, taskListener);
        delayHandler.postDelayed(imageDelayer, currentShowImageTask.delay*1000);
    }

    public static class URLDelayer implements Runnable {
        private final PepperTask rShowURLTask;
        private final Callbacks rActivity;
        private final WebSocket rWebSocket;
        private final PepperTaskListener rTaskListener;

        public URLDelayer(PepperTask showURLTask,
                            Callbacks activity,
                            WebSocket webSocket,
                            PepperTaskListener taskListener) {
            rShowURLTask = showURLTask;
            rActivity = activity;
            rWebSocket = webSocket;
            rTaskListener = taskListener;
        }

        @Override
        public void run() {
            // TODO: chromium error texture_definition.cc eglCreateImageKHR for cross-thread sharing failed
            rActivity.loadURL(new String(rShowURLTask.byteContent));
            // TODO: programmatic change (show/hide a view) between imageView and webView
            rWebSocket.send(String.format("{\"action_success\": \"%s\"}", rShowURLTask.id));
            rTaskListener.setNewShowURLTaskAvailable(false);

        }
    }

    public void showURL(PepperTask currentShowURLTask) {
        URLDelayer urlDelayer = new URLDelayer(currentShowURLTask, activity, webSocket, taskListener);
        delayHandler.postDelayed(urlDelayer, currentShowURLTask.delay*1000);
    }

    // callbacks

    public void registerClient(Activity activity) {
        this.activity = (Callbacks) activity;
    }

    public interface Callbacks {
        void setImage(Bitmap bitmap);

        void setImageResource(Integer resID);

        void setConnectionStatus(Boolean isConnectionUp);

        void loadURL(String uri);

        void clearFragment();

        void auth(String auth_code, String helptext);
    }
}
