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
                        Boolean isNewSayTask, isNewMoveTask, isNewShowImageTask, isNewShowURLTask, isNewStopVideoTask;

                        isNewSayTask = taskListener.getNewSayTaskAvailable();
                        isNewMoveTask = taskListener.getNewMoveTaskAvailable();
                        isNewShowImageTask = taskListener.getNewShowImageTaskAvailable();
                        isNewShowURLTask = taskListener.getNewShowURLTaskAvailable();
                        isNewStopVideoTask = taskListener.getNewStopVideoTaskAvailable();

                        // clear image view for any new task
                        if (isNewSayTask || isNewMoveTask || isNewShowImageTask || isNewShowURLTask) {
                            activity.setImageResource(android.R.color.transparent);
                        }

                        if (isNewSayTask) {
                            say(taskListener.getCurrentSayTask());
                        }
                        if (isNewMoveTask) {
                            move(taskListener.getCurrentMoveTask());
                        }
                        if (isNewShowImageTask) {
                            showImage(taskListener.getCurrentShowImageTask());
                        }
                        if (isNewShowURLTask) {
                            showURL(taskListener.getCurrentShowURLTask());
                        }
                        if (isNewStopVideoTask) {
                            stopVideo();
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
            delayTaskIfNeeded(currentMoveTask);
            move.async().run().thenConsume(voidFuture -> {
                if (voidFuture.isSuccess()) {
                    Log.d(TAG, "Animation has finished");
                    webSocket.send(String.format("{\"action_success\": \"%s\"}", currentMoveTask.id));
                } else if (voidFuture.hasError()) {
                    Log.d(TAG, "Animation had an error:", voidFuture.getError());
                    webSocket.send(String.format("{\"action_error\": \"%s\"}", currentMoveTask.id));
                }
            });
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
            Log.i(TAG, "say: Playing...");
            mediaPlayer.start();
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

    public void showImage(PepperTask currentShowImageTask) {
        byte[] imageContent = currentShowImageTask.byteContent;
        Bitmap image = BitmapFactory.decodeByteArray(imageContent, 0, imageContent.length);
        delayTaskIfNeeded(currentShowImageTask);
        activity.setImage((image));
        webSocket.send(String.format("{\"action_success\": \"%s\"}", currentShowImageTask.id));
        taskListener.setNewShowImageTaskAvailable(false);
    }

    public void showURL(PepperTask currentShowURLTask) {
        delayTaskIfNeeded(currentShowURLTask);
        activity.loadURL(new String(currentShowURLTask.byteContent)); // TODO: chromium error texture_definition.cc eglCreateImageKHR for cross-thread sharing failed
        // TODO: programmatic change (show/hide a view) between imageView and webView
        webSocket.send(String.format("{\"action_success\": \"%s\"}", currentShowURLTask.id));
        taskListener.setNewShowURLTaskAvailable(false);
    }

    public void stopVideo() {
        activity.stopVideo();
    }

    private void delayTaskIfNeeded(PepperTask task) {
        if (task.delay > 0) {
            try {
                Thread.sleep(task.delay);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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

        void stopVideo();
    }
}
