package ee.ims.tomato;

import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.aldebaran.qi.sdk.QiContext;
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks;
import com.aldebaran.qi.sdk.builder.AnimateBuilder;
import com.aldebaran.qi.sdk.builder.AnimationBuilder;
import com.aldebaran.qi.sdk.builder.SayBuilder;
import com.aldebaran.qi.sdk.object.actuation.Animate;
import com.aldebaran.qi.sdk.object.actuation.Animation;
import com.aldebaran.qi.sdk.object.conversation.Say;

import java.util.concurrent.Executor;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;

public class CommunicationService extends Service implements RobotLifecycleCallbacks {
    private String TAG = "CommunicationService";
    private final IBinder binder = new LocalBinder();
    private Executor executor;
    private WebSocket webSocket;
    private Boolean isWebSocketConnected;
    private int connectionEstablishingDelayMillis = 1000;
    private PepperTaskListener taskListener;
    private Callbacks activity;
    private QiContext qiContext;
    private String serverURL;
    private BroadcastReceiver websocketMessageReceiver;
    private BroadcastReceiver websocketUpReceiver;
    private BroadcastReceiver websocketDownReceiver;

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
        this.qiContext = qiContext;
        taskListener = new PepperTaskListener(qiContext);

        websocketMessageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                executor.execute(() -> {
                    Boolean isNewSayTask, isNewMoveTask, isNewShowImageTask, isNewShowURLTask;

                    isNewSayTask = taskListener.getNewSayTaskAvailable();
                    isNewMoveTask = taskListener.getNewMoveTaskAvailable();
                    isNewShowImageTask = taskListener.getNewShowImageTaskAvailable();
                    isNewShowURLTask = taskListener.getNewShowURLTaskAvailable();

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
                    Log.d(TAG, "animation has finished");
                } else if (voidFuture.hasError()) {
                    Log.d(TAG, "animation has the error:", voidFuture.getError());
                }
            });
        }

        taskListener.setNewMoveTaskAvailable(false);
    }

    public void say(PepperTask currentSayTask) {
        Say say = SayBuilder.with(qiContext).withText(currentSayTask.content).build();
        say.async().run().thenConsume(voidFuture -> {
            if (voidFuture.isSuccess()) {
                Log.d(TAG, "say action has finished");
            } else if (voidFuture.hasError()) {
                Log.d(TAG, "say action has the error:", voidFuture.getError());
            }
        });
        taskListener.setNewSayTaskAvailable(false);
    }

    public void showImage(PepperTask currentShowImageTask) {
        byte[] imageContent = currentShowImageTask.byteContent;
        Bitmap image = BitmapFactory.decodeByteArray(imageContent, 0, imageContent.length);
        delayTaskIfNeeded(currentShowImageTask);
        activity.setImage((image));
        taskListener.setNewShowImageTaskAvailable(false);
    }

    public void showURL(PepperTask currentShowURLTask) {
        delayTaskIfNeeded(currentShowURLTask);
        activity.loadURL(new String(currentShowURLTask.byteContent)); // TODO: chromium error texture_definition.cc eglCreateImageKHR for cross-thread sharing failed
        // TODO: programmatic change (show/hide a view) between imageView and webView
        taskListener.setNewShowURLTaskAvailable(false);
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
    }
}
