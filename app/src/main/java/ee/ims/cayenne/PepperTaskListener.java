package ee.ims.cayenne;

import android.content.Intent;
import android.util.Base64;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.aldebaran.qi.sdk.QiContext;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class PepperTaskListener extends WebSocketListener {
    public static String BROADCAST_WEBSOCKET_UP_KEY = "ee.ims.cayenne.PepperTaskListener.WEBSOCKET-UP";
    public static String BROADCAST_WEBSOCKET_MESSAGE_KEY = "ee.ims.cayenne.PepperTaskListener.WEBSOCKET-MESSAGE";
    public static String BROADCAST_WEBSOCKET_DOWN_KEY = "ee.ims.cayenne.PepperTaskListener.WEBSOCKET-DOWN";

    private static final String TAG = "PepperTaskListener";
    private static final int NORMAL_CLOSURE_STATUS = 1000;

    private QiContext qiContext;
    private PepperTask currentAuthTask = new PepperTask();
    private PepperTask currentSayTask = new PepperTask();
    private PepperTask currentMoveTask = new PepperTask();
    private PepperTask currentShowImageTask = new PepperTask();
    private PepperTask currentShowURLTask = new PepperTask();
    private Boolean isNewAuthTaskAvailable = false;
    private Boolean isNewSayTaskAvailable = false;
    private Boolean isNewMoveTaskAvailable = false;
    private Boolean isNewShowImageTaskAvailable = false;
    private Boolean isNewShowURLTaskAvailable = false;
    private Boolean isNewClearImageTaskAvailable = false;
    private Boolean isNewClearFragmentTaskAvailable = false;
    private Boolean isWebSocketOpened = false;

    PepperTaskListener(QiContext context) {
        qiContext = context;
    }

    // Getter-setters

    public PepperTask getCurrentAuthTask() {
        return currentAuthTask;
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

    public PepperTask getCurrentShowURLTask() {
        return currentShowURLTask;
    }

    public Boolean getNewSayTaskAvailable() {
        return isNewSayTaskAvailable;
    }

    public void setNewAuthTaskAvailable(Boolean newAuthTaskAvailable) {
        isNewAuthTaskAvailable = newAuthTaskAvailable;
    }

    public Boolean getNewAuthTaskAvailable() {
        return isNewAuthTaskAvailable;
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

    public Boolean getNewShowURLTaskAvailable() {
        return isNewShowURLTaskAvailable;
    }

    public void setNewShowURLTaskAvailable(Boolean newShowURLTaskAvailable) {
        isNewShowURLTaskAvailable = newShowURLTaskAvailable;
    }

    public Boolean getNewClearImageTaskAvailable() {
        return isNewClearImageTaskAvailable;
    }

    public void setNewClearImageTaskAvailable(Boolean newClearImageTaskAvailable) {
        isNewClearImageTaskAvailable = newClearImageTaskAvailable;
    }

    public Boolean getNewClearFragmentTaskAvailable() {
        return isNewClearFragmentTaskAvailable;
    }

    public void setNewClearFragmentTaskAvailable(Boolean newClearFragmentTaskAvailable) {
        isNewClearFragmentTaskAvailable = newClearFragmentTaskAvailable;
    }

    public Boolean getWebSocketOpened() {
        return isWebSocketOpened;
    }

    // WebSocketListener implementation of okhttp3

    @Override
    public void onOpen(@NotNull WebSocket webSocket, Response response) {
        String resources = getBuiltInResources();
        webSocket.send(resources);
        Log.d(TAG, String.format("onOpen: resources information sent: %s", resources));

        isWebSocketOpened = true;
        Log.d(TAG, "onOpen: websocket is opened");

        LocalBroadcastManager.getInstance(qiContext).sendBroadcast(new Intent(BROADCAST_WEBSOCKET_UP_KEY));
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
                String id = msgJSON.getString("id");

                if (command.equals("auth")) {
                    currentAuthTask.content = content;
                    currentAuthTask.name = name;
                    currentAuthTask.id = id;
                    isNewAuthTaskAvailable = true;
                }
                if (command.equals("say")) {
                    currentSayTask.command = command;
                    byte[] byteContent = android.util.Base64.decode(content.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE);
                    currentSayTask.content = new String(byteContent);
                    currentSayTask.name = name;
                    currentSayTask.delay = delay;
                    currentSayTask.id = id;
                    isNewSayTaskAvailable = true;
                }
                if (command.equals("move")) {
                    currentMoveTask.command = command;
                    byte[] byteContent = android.util.Base64.decode(content.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);
                    currentMoveTask.content = new String(byteContent);
                    currentMoveTask.name = name;
                    currentMoveTask.delay = delay;
                    currentMoveTask.id = id;
                    isNewMoveTaskAvailable = true;
                }
                if (command.equals("show_image")) {
                    currentShowImageTask.command = command;
                    currentShowImageTask.byteContent = Base64.decode(content.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);
                    currentShowImageTask.name = name;
                    currentShowImageTask.delay = delay;
                    currentShowImageTask.id = id;
                    isNewShowImageTaskAvailable = true;
                }
                if (command.equals("show_url")) {
                    currentShowURLTask.command = command;
                    currentShowURLTask.byteContent = Base64.decode(content.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE);
                    currentShowURLTask.name = name;
                    currentShowURLTask.delay = delay;
                    currentShowURLTask.id = id;
                    isNewShowURLTaskAvailable = true;
                }
                if (command.equals("clear_image")) {
                    isNewClearImageTaskAvailable = true;
                }
                if (command.equals("clear_fragment")) {
                    isNewClearFragmentTaskAvailable = true;
                }

                Log.d(TAG, String.format("command: %s, name: %s, delay: %d",
                        command, name, delay));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        LocalBroadcastManager.getInstance(qiContext).sendBroadcast(new Intent(BROADCAST_WEBSOCKET_MESSAGE_KEY));
    }

    @Override
    public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
        Log.d(TAG, "onMessage using bytes is not implemented");
        LocalBroadcastManager.getInstance(qiContext).sendBroadcast(new Intent(BROADCAST_WEBSOCKET_MESSAGE_KEY));
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        Log.d(TAG, "onClosing: " + reason);
        webSocket.close(NORMAL_CLOSURE_STATUS, null);
        isWebSocketOpened = false;
        LocalBroadcastManager.getInstance(qiContext).sendBroadcast(new Intent(BROADCAST_WEBSOCKET_DOWN_KEY));
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

        LocalBroadcastManager.getInstance(qiContext).sendBroadcast(new Intent(BROADCAST_WEBSOCKET_DOWN_KEY));
    }

    @Override
    public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
        super.onClosed(webSocket, code, reason);
        Log.d(TAG, "onClosed: " + reason);
        isWebSocketOpened = false;
        LocalBroadcastManager.getInstance(qiContext).sendBroadcast(new Intent(BROADCAST_WEBSOCKET_DOWN_KEY));
    }

    // Other methods

    // getBuiltInResources collects motion files already present in QiSDK and dumps it as JSON string.
    private String getBuiltInResources() {
        Field[] fields = R.raw.class.getFields();
        String[] allRawResources = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            allRawResources[i] = fields[i].getName();
        }
        JSONObject obj = new JSONObject();
        try {
            obj.put("moves", new JSONArray(allRawResources));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return obj.toString();
    }
}