package ee.ims.tomato;

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
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

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
    Boolean isNewTask = false;

    private static class PepperTask {
        public String command;
        public String content;
        public Integer delay;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Pepper initiation
        QiSDK.register(this, this);

        outputText = findViewById(R.id.outputText);
        outputText.setText("Output goes here.");
    }

    // Pepper RobotLifecycleCallbacks implementation

    @Override
    public void onRobotFocusGained(QiContext qiContext) {
        Log.i("PepperCallbacks", "focus gained");
        this.qiContext = qiContext;

        // Establishing a connection with the server with Pepper tasks
        String serverURL = "ws://10.0.2.2:8080/pepper/initiate";
//        String serverURL = "ws://192.168.1.227:8080/pepper/initiate";
        Request request = new Request.Builder().url(serverURL).build();
        PepperTasksListener listener = new PepperTasksListener();
        OkHttpClient client = new OkHttpClient();
        WebSocket webSocket = client.newWebSocket(request, listener);

        for (; ; ) {
            if (isNewTask) {
                if (task.command.equals("say")) {
                    runOnUiThread(() -> outputText.setText(task.command + ": " + task.content));

                    Say say = SayBuilder.with(qiContext).withText(task.content).build();
                    say.run();
                }

                if (task.command.equals("move")) {
                    runOnUiThread(() -> outputText.setText(task.command));

                    if (task.delay > 0) {
                        try {
                            Thread.sleep(task.delay);
                            Animation motion = AnimationBuilder.with(qiContext).withTexts(task.content).build();
                            Animate move = AnimateBuilder.with(qiContext).withAnimation(motion).build();
                            move.run();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        Animation motion = AnimationBuilder.with(qiContext).withTexts(task.content).build();
                        Animate move = AnimateBuilder.with(qiContext).withAnimation(motion).build();
                        move.run();
                    }
                }

                isNewTask = false;
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

    // WebSocket implementation with okhttp3
    public class PepperTasksListener extends WebSocketListener {
        private static final int NORMAL_CLOSURE_STATUS = 1000;

        @Override
        public void onOpen(@NotNull WebSocket webSocket, Response response) {
            Log.i("WebSocket", "opened");
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
                    task.command = msgJSON.getString("command");
                    task.content = msgJSON.getString("content");
                    task.delay = msgJSON.getInt("delay");
                    Log.d("WebSocket", String.format("command: %s, delay: %d", task.command, task.delay));
                    isNewTask = true;
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
        public void onFailure(@NotNull WebSocket webSocket, Throwable throwable, Response response) {
            Log.e("WebSocket", throwable.getMessage());
            Log.i("WebSocket", response.toString());
        }
    }
}