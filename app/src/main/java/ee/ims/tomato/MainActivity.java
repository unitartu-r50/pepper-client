package ee.ims.tomato;

import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import com.aldebaran.qi.sdk.Qi;
import com.aldebaran.qi.sdk.QiContext;
import com.aldebaran.qi.sdk.QiSDK;
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks;
import com.aldebaran.qi.sdk.builder.AnimateBuilder;
import com.aldebaran.qi.sdk.builder.AnimationBuilder;
import com.aldebaran.qi.sdk.builder.SayBuilder;
import com.aldebaran.qi.sdk.design.activity.RobotActivity;
import com.aldebaran.qi.sdk.object.actuation.Animate;
import com.aldebaran.qi.sdk.object.actuation.Animation;
import com.aldebaran.qi.sdk.object.conversation.Say;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import okio.Utf8;

public class MainActivity extends RobotActivity implements RobotLifecycleCallbacks {
    QiContext qiContext;
    TextView outputText;

    PepperTask task = new PepperTask();
    PepperTask currentSayTask = new PepperTask();
    PepperTask currentMoveTask = new PepperTask();
    Boolean isNewTask = false;
    Boolean isNewSayTask = false;
    Boolean isNewMoveTask = false;
    String packageName;
    String[] allRawResources;
    String allRawResourcesString;
    WebSocket webSocket;

    private static class PepperTask {
        public String command;
        public String content;
        public String name;
        public Integer delay;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Pepper initiation
        QiSDK.register(this, this);

        // some fields for a basic feedback
        outputText = findViewById(R.id.outputText);
        outputText.setText("Output goes here.");

        // getting all motion files to send it to the web server for a user to use
        Field[] fields = R.raw.class.getFields();
        String[] allRawResources = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            allRawResources[i] = fields[i].getName();
        }
        Log.d("onCreate", String.format("found resources: %d, first of them: %s", allRawResources.length, allRawResources[0]));
        JSONObject obj = new JSONObject();
        try {
            obj.put("moves", new JSONArray(allRawResources));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        allRawResourcesString = obj.toString();

        packageName = getPackageName();
    }

    // Pepper RobotLifecycleCallbacks implementation

    @Override
    public void onRobotFocusGained(QiContext qiContext) {
        Log.i("PepperCallbacks", "focus gained");
        this.qiContext = qiContext;

        establishConnection();

        // main loop for the robot control
        for (; ; ) {
            if (isNewSayTask) {
                runOnUiThread(() -> outputText.setText(currentSayTask.command + ": " + currentSayTask.content));

                Say say = SayBuilder.with(qiContext).withText(currentSayTask.content).build();
                say.async().run().thenConsume(voidFuture -> {
                    if (voidFuture.isSuccess()) {
                        Log.d("onRobotFocusGained", "say action has finished");
                    } else if (voidFuture.hasError()) {
                        Log.d("onRobotFocusGained", "say action has the error:", voidFuture.getError());
                    }
                });
                isNewSayTask = false;
            }

            if (isNewMoveTask) {
                runOnUiThread(() -> outputText.setText(currentMoveTask.command));

                Log.d("onRobotFocusGained", "task move");

                Animation motion = null;

                if (currentMoveTask.content.length() == 0) {
                    Log.d("onRobotFocusGained", "empty content");
                    int resID = getResources().getIdentifier(currentMoveTask.name, "raw", packageName);
                    if (resID != 0) {
                        motion = AnimationBuilder.with(qiContext).withResources(resID).build();
                    } else {
                        Log.d("onRobotFocusGained", String.format("can't find resource %s", currentMoveTask.name));
                    }
                } else {
                    Log.d("onRobotFocusGained", "content is present");
                    motion = AnimationBuilder.with(qiContext).withTexts(currentMoveTask.content).build();
                }

                if (motion != null) {
                    Animate move = AnimateBuilder.with(qiContext).withAnimation(motion).build();
                    Log.d("onRobotFocusGained", "animate is ready");

                    if (currentMoveTask.delay > 0) {
                        Log.d("onRobotFocusGained", "delay is needed");
                        try {
                            Thread.sleep(currentMoveTask.delay);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    Log.d("onRobotFocusGained", "running");
                    move.async().run().thenConsume(voidFuture -> {
                        if (voidFuture.isSuccess()) {
                            Log.d("onRobotFocusGained", "animation has finished");
                        } else if (voidFuture.hasError()) {
                            Log.d("onRobotFocusGained", "animation has the error:", voidFuture.getError());
                        }
                    });
                }

                isNewMoveTask = false;
            }
        }
    }

    @Override
    public void onRobotFocusLost() {
        Log.i("PepperCallbacks", "focus lost");
        this.qiContext = null;
    }

    @Override
    public void onRobotFocusRefused(String reason) {
        Log.i("PepperCallbacks", "focus refused: " + reason);
    }

    // Helper methods

    private void establishConnection() {
        // Establishing a connection with the server with Pepper tasks
//        String serverURL = "ws://10.0.2.2:8080/pepper/initiate";
        String serverURL = "ws://192.168.1.227:8080/pepper/initiate";
        Request request = new Request.Builder().url(serverURL).build();
        PepperTasksListener listener = new PepperTasksListener();
        OkHttpClient client = new OkHttpClient();
        webSocket = client.newWebSocket(request, listener);
    }

    // WebSocket implementation with okhttp3
    public class PepperTasksListener extends WebSocketListener {
        private static final int NORMAL_CLOSURE_STATUS = 1000;

        @Override
        public void onOpen(@NotNull WebSocket webSocket, Response response) {
            Log.i("WebSocket", "opened");

            // informing the web server about available resources
            webSocket.send(allRawResourcesString);
            Log.d("PepperTasksListener", String.format("resources information sent: %s", allRawResourcesString));
        }

        @Override
        public void onMessage(@NotNull WebSocket webSocket, String message) {
            Log.d("WebSocketListener", "onMessage string");
            JSONObject msgJSON = null;
            try {
                msgJSON = new JSONObject(message);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (msgJSON != null) {
                Log.d("WebSocket", msgJSON.toString());
                try {
                    String command = msgJSON.getString("command");
                    String content = msgJSON.getString("content");
                    String name = msgJSON.getString("name");
                    Integer delay = msgJSON.getInt("delay");

                    if (command.equals("say")) {
                        currentSayTask.command = command;
                        currentSayTask.content = content;
                        currentSayTask.name = name;
                        currentSayTask.delay = delay;
                        isNewSayTask = true;
                    }
                    if (command.equals("move")) {
                        currentMoveTask.command = command;
                        currentMoveTask.content = content;
                        currentMoveTask.name = name;
                        currentMoveTask.delay = delay;
                        isNewMoveTask = true;
                    }

                    Log.d("WebSocket", String.format("command: %s, name: %s, delay: %d",
                            command, name, delay));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
            Log.d("WebSocketListener", "onMessage bytes");
            JSONObject msgJSON = null;
            try {
                msgJSON = new JSONObject(bytes.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (msgJSON != null) {
                try {
                    task.command = msgJSON.getString("command");
                    task.content = msgJSON.getString("content");
                    task.name = msgJSON.getString("name");
                    task.delay = msgJSON.getInt("delay");
                    isNewTask = true;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            Log.i("WebSocket", "closing: " + reason);
            webSocket.close(NORMAL_CLOSURE_STATUS, null);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable throwable, Response response) {
            webSocket.close(NORMAL_CLOSURE_STATUS, null);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Log.d("PepperTasksListener", "re-establishing connection");
            establishConnection();
        }
    }
}