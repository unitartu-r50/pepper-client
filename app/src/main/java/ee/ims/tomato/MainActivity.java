package ee.ims.tomato;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.aldebaran.qi.sdk.Qi;
import com.aldebaran.qi.sdk.QiContext;
import com.aldebaran.qi.sdk.QiSDK;
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks;
import com.aldebaran.qi.sdk.builder.SayBuilder;
import com.aldebaran.qi.sdk.design.activity.RobotActivity;
import com.aldebaran.qi.sdk.object.conversation.Say;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class MainActivity extends RobotActivity implements RobotLifecycleCallbacks {
    QiContext qiContext;
    TextView outputText;

    PepperTask task = new PepperTask();
    Boolean isNewTask = false;

    private static class PepperTask {
        public String command;
        public String content;
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
        Request request = new Request.Builder().url(serverURL).build();
        PepperTasksListener listener = new PepperTasksListener();
        OkHttpClient client = new OkHttpClient();
        WebSocket webSocket = client.newWebSocket(request, listener);

        for (;;) {
            if (isNewTask) {
                runOnUiThread(() -> outputText.setText(task.command + ": " + task.content));
                Say say = SayBuilder.with(qiContext).withText(task.content).build();
                say.run();
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
            JSONObject msgJSON = null;
            try {
                msgJSON = new JSONObject(message);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (msgJSON != null) {
                Log.i("WebSocket", msgJSON.toString());
                try {
                    task.command = msgJSON.getString("Command");
                    task.content = msgJSON.getString("Content");
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