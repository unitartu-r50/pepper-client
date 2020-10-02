package ee.ims.tomato;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

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

import android.os.IBinder;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SessionActivity extends RobotActivity implements CommunicationService.Callbacks {
    // Android specific declarations
    String packageName;
    String TAG = "SessionMainActivity";

    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    // QiSDK specific declarations
    QiContext qiContext;

    // UI declarations
    ImageView imageView;
    TextView serverConnectionStatusText;

    PepperTaskListener taskListener;

    String serverURL;

    CommunicationService communication;
    Boolean isCommunicationBound;
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CommunicationService.LocalBinder binder = (CommunicationService.LocalBinder) service;
            communication = binder.getService(executorService);
            communication.registerClient(SessionActivity.this);
            communication.setServerURL(serverURL);
            isCommunicationBound = true;
            QiSDK.register(SessionActivity.this, communication);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isCommunicationBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session);

        // UI
        imageView = (ImageView) findViewById(R.id.imageView);

        // other variables
        packageName = getPackageName();

        // service binding
        Intent serviceIntent = new Intent(this, CommunicationService.class);
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);

        // getting passed parameters from the parent activity
        serverURL = getIntent().getStringExtra(MainActivity.EXTRA_SERVER_URL);
    }

    // CommunicationService callbacks implementation

    @Override
    public void setImage(Bitmap bitmap) {
        runOnUiThread(() -> imageView.setImageBitmap(bitmap));
    }

    @Override
    public void setImageResource(Integer resID) {
        runOnUiThread(() -> imageView.setImageResource(resID));
    }

    @Override
    public void setServerConnectionStatus(String status) {
        runOnUiThread(() -> serverConnectionStatusText.setText(status));
    }
}