package ee.ims.tomato;

import android.content.Context;
import android.content.Intent;
import android.util.Base64;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.aldebaran.qi.sdk.QiContext;
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class PepperTaskListener extends WebSocketListener {
    private static final String TAG = "PepperTaskListener";
    private static final int NORMAL_CLOSURE_STATUS = 1000;

    private QiContext qiContext;

    private PepperTask currentSayTask = new PepperTask();
    private PepperTask currentMoveTask = new PepperTask();
    private PepperTask currentShowImageTask = new PepperTask();
    private Boolean isNewSayTaskAvailable = false;
    private Boolean isNewMoveTaskAvailable = false;
    private Boolean isNewShowImageTaskAvailable = false;
    private Boolean isWebSocketOpened = false;

    PepperTaskListener(QiContext context) {
        qiContext = context;
    }

    public PepperTask getCurrentSayTask() {
        return currentSayTask;
    }

    public PepperTask getCurrentMoveTask() {
        return currentMoveTask;
    }

    public PepperTask getCurrentShowImageTask() {
        return currentShowImageTask;
    }

    public Boolean getNewSayTaskAvailable() {
        return isNewSayTaskAvailable;
    }

    public void setNewSayTaskAvailable(Boolean newSayTaskAvailable) {
        isNewSayTaskAvailable = newSayTaskAvailable;
    }

    public Boolean getNewMoveTaskAvailable() {
        return isNewMoveTaskAvailable;
    }

    public void setNewMoveTaskAvailable(Boolean newMoveTaskAvailable) {
        isNewMoveTaskAvailable = newMoveTaskAvailable;
    }

    public Boolean getNewShowImageTaskAvailable() {
        return isNewShowImageTaskAvailable;
    }

    public void setNewShowImageTaskAvailable(Boolean newShowImageTaskAvailable) {
        isNewShowImageTaskAvailable = newShowImageTaskAvailable;
    }

    public Boolean getWebSocketOpened() {
        return isWebSocketOpened;
    }

    // WebSocketListener implementation of okhttp3

    @Override
    public void onOpen(@NotNull WebSocket webSocket, Response response) {
        // getting all motion files to send it to the web server for a user to use
        String resources = getBuiltInResources();
        webSocket.send(resources);
        Log.d(TAG, String.format("onOpen: resources information sent: %s", resources));

        isWebSocketOpened = true;

        Log.d(TAG, "onOpen: websocket is opened");
    }

    @Override
    public void onMessage(@NotNull WebSocket webSocket, String message) {
        Log.d(TAG, "onMessage string");

        JSONObject msgJSON = null;
        try {
            msgJSON = new JSONObject(message);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (msgJSON != null) {
            Log.d(TAG, msgJSON.toString());
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
                    isNewSayTaskAvailable = true;
                }
                if (command.equals("move")) {
                    currentMoveTask.command = command;
                    byte[] byteContent = android.util.Base64.decode(content, Base64.DEFAULT);
                    currentMoveTask.content = new String(byteContent);
                    currentMoveTask.name = name;
                    currentMoveTask.delay = delay;
                    isNewMoveTaskAvailable = true;
                }
                if (command.equals("show_image")) {
                    currentShowImageTask.command = command;
                    currentShowImageTask.byteContent = Base64.decode(content, Base64.DEFAULT);
                    currentShowImageTask.name = name;
                    currentShowImageTask.delay = delay;
                    isNewShowImageTaskAvailable = true;
                }

                Log.d(TAG, String.format("command: %s, name: %s, delay: %d",
                        command, name, delay));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        // TODO: clean up intent keys
        // TODO: add other intents in onClosing, onFAilture, etc
        Intent intent = new Intent("my-filter");
        intent.putExtra("new-message", true);
        LocalBroadcastManager.getInstance(qiContext).sendBroadcast(intent);
    }

    @Override
    public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
        Log.d(TAG, "onMessage using bytes is not implemented");
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        Log.d(TAG, "onClosing: " + reason);
        webSocket.close(NORMAL_CLOSURE_STATUS, null);
        isWebSocketOpened = false;
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable throwable, Response response) {
        webSocket.close(NORMAL_CLOSURE_STATUS, null);
        isWebSocketOpened = false;

        String message = "<empty>", headers = "<empty>";
        if (response != null) {
            message = response.message();
            headers = response.headers().toString();
        }
        Log.d(TAG, "onFailure: " + message + " response headers: " + headers);
    }

    @Override
    public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
        super.onClosed(webSocket, code, reason);
        Log.d(TAG, "onClosed: " + reason);
        isWebSocketOpened = false;
    }

    // Other methods

    // getBuiltInResources collects motion files already present in QiSDK and dumps it as JSON string.
    private String getBuiltInResources() {
        Field[] fields = R.raw.class.getFields();
        String[] allRawResources = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            allRawResources[i] = fields[i].getName();
        }
        Log.d(TAG, String.format("found resources: %d, first of them: %s", allRawResources.length, allRawResources[0]));
        JSONObject obj = new JSONObject();
        try {
            obj.put("moves", new JSONArray(allRawResources));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return obj.toString();
    }
}