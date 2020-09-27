package ee.ims.tomato;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.Formatter;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
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
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.StringJoiner;

import kotlin.Pair;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.internal.http2.Header;
import okio.ByteString;
import okio.Utf8;

public class MainActivity extends RobotActivity implements RobotLifecycleCallbacks {
    QiContext qiContext;

    // UI
//    TextView outputText;
    EditText machineIPInput;
    TextView serverConnectionStatusText;
    ImageView imageView;

    // Internal
    PepperTask currentSayTask = new PepperTask();
    PepperTask currentMoveTask = new PepperTask();
    PepperTask currentShowImageTask = new PepperTask();
    Boolean isNewTask = false;
    Boolean isNewSayTask = false;
    Boolean isNewMoveTask = false;
    Boolean isNewShowImageTask = false;
    String packageName;
    String[] allRawResources;
    String allRawResourcesString;
    WebSocket webSocket;
    String serverURL = "ws://10.0.2.2:8080/pepper/initiate";

    private static class PepperTask {
        public String command;
        public String content;
        public byte[] byteContent;
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
        machineIPInput = findViewById(R.id.editMachineIP);
        serverConnectionStatusText = findViewById(R.id.serverConnectionStatusText);
        imageView = findViewById(R.id.imageView);

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

            if (isNewShowImageTask) {
                byte[] imageContent = currentShowImageTask.byteContent;
                Bitmap image = BitmapFactory.decodeByteArray(imageContent, 0, imageContent.length);
                Log.d("onRobotFocusGained", "bitmap decoded");
                runOnUiThread(() -> {
                    imageView.setImageBitmap(image);
                });

                isNewShowImageTask = false;
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
//        String serverURL = "ws://192.168.1.227:8080/pepper/initiate";
//        String serverURL = "ws://192.168.1.45:8080/pepper/initiate";
        Log.d("establishConnection", String.format("Establishing a connection to %s", serverURL));
        if (webSocket != null) {
            webSocket.close(1000, null);
        }

        Request request = new Request.Builder().url(serverURL).build();
        PepperTasksListener listener = new PepperTasksListener();
        OkHttpClient client = new OkHttpClient();
        webSocket = client.newWebSocket(request, listener);

        Log.d("establishConnection", String.format("Connection to %s has been established", serverURL));
    }

    // WebSocket implementation with okhttp3
    public class PepperTasksListener extends WebSocketListener {
        private static final int NORMAL_CLOSURE_STATUS = 1000;

        @Override
        public void onOpen(@NotNull WebSocket webSocket, Response response) {
            Log.d("PepperTasksListener", "websocket is opened");

            // informing the web server about available resources
            webSocket.send(allRawResourcesString);
            Log.d("PepperTasksListener", String.format("resources information sent: %s", allRawResourcesString));
            runOnUiThread(() -> serverConnectionStatusText.setText("Pepper is connected to the server"));
        }

        @Override
        public void onMessage(@NotNull WebSocket webSocket, String message) {
            // clearing image view on any new message
            runOnUiThread(() -> {
                imageView.setImageResource(android.R.color.transparent);
            });

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
                        byte[] byteContent = android.util.Base64.decode(content, Base64.DEFAULT);
                        currentSayTask.content = new String(byteContent);
                        currentSayTask.name = name;
                        currentSayTask.delay = delay;
                        isNewSayTask = true;
                    }
                    if (command.equals("move")) {
                        currentMoveTask.command = command;
                        byte[] byteContent = android.util.Base64.decode(content, Base64.DEFAULT);
                        currentMoveTask.content = new String(byteContent);
                        currentMoveTask.name = name;
                        currentMoveTask.delay = delay;
                        isNewMoveTask = true;
                    }
                    if (command.equals("show_image")) {
                        currentShowImageTask.command = command;
                        currentShowImageTask.byteContent = Base64.decode(content, Base64.DEFAULT);
                        currentShowImageTask.name = name;
                        currentShowImageTask.delay = delay;
                        isNewShowImageTask = true;
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
            Log.d("PepperTasksListener", "not implemented");
//            if (msgJSON != null) {
//                try {
//                    String command = msgJSON.getString("command");
//                    String content = msgJSON.getString("content");
//                    String name = msgJSON.getString("name");
//                    Integer delay = msgJSON.getInt("delay");
//
//                    if (command.equals("show_image")) {
//                        currentShowImageTask.command = command;
//                        currentShowImageTask.content = content;
//                        currentShowImageTask.name = name;
//                        currentShowImageTask.delay = delay;
//                        isNewShowImageTask = true;
//                    } else {
//                        Log.d("PepperTasksListener", "onMessage got unknown command" + command);
//                    }
//
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                }
//            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            Log.d("PepperTasksListener", "closing: " + reason);
            webSocket.close(NORMAL_CLOSURE_STATUS, null);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable throwable, Response response) {
            webSocket.close(NORMAL_CLOSURE_STATUS, null);
            Log.d("PepperTasksListener", "failure: " + response.message() + " response headers: " + response.headers().toString());

            runOnUiThread(() -> serverConnectionStatusText.setText("Pepper isn't connected to the server"));
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
            super.onClosed(webSocket, code, reason);
            Log.d("PepperTasksListener", "closed: " + reason);
        }
    }

    // UI events

    public void setMachineIP(View view) {
        String IPString = machineIPInput.getText().toString();
        try {
            InetSocketAddress ip = new InetSocketAddress(IPString, 8080);
            serverURL = String.format("ws:/%s/pepper/initiate", ip.toString());
            establishConnection();
        } catch (Exception err) {
            Log.d("setMachineIP", String.format("Failed to parse the IP address: %s, error: %s", IPString, err));
        }
    }
}