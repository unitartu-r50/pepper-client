package ee.ims.tomato;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.aldebaran.qi.sdk.QiContext;
import com.aldebaran.qi.sdk.QiSDK;
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks;
import com.aldebaran.qi.sdk.design.activity.RobotActivity;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends RobotActivity {
    // Android specific declarations
    String packageName;
    String TAG = "MainActivity";
//    public static String EXTRA_TASK_LISTENER = "ee.ims.tomato.MainActivity.taskListener";
    public static String EXTRA_SERVER_URL = "ee.ims.tomato.MainActivity.serverURL";

//    private ExecutorService executorService = Executors.newFixedThreadPool(1);

    // QiSDK specific declarations
//    QiContext qiContext;

    // UI declarations
    EditText machineIPInput;
//    TextView serverConnectionStatusText;
    Button startButton;
    Button setIPButton;

    String serverURL = "ws://10.0.2.2:8080/pepper/initiate";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Pepper initiation
//        QiSDK.register(this, this);

        // some fields for a basic feedback
        machineIPInput = findViewById(R.id.editMachineIP);
//        serverConnectionStatusText = findViewById(R.id.serverConnectionStatusText);

        startButton = findViewById(R.id.start);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent activityIntent = new Intent(getApplicationContext(), SessionActivity.class);
                activityIntent.putExtra(EXTRA_SERVER_URL, serverURL);
                startActivity(activityIntent);
            }
        });

        setIPButton = findViewById(R.id.setIPButton);
        setIPButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String IPString = machineIPInput.getText().toString();
                try {
                    InetSocketAddress ip = new InetSocketAddress(IPString, 8080);
                    serverURL = String.format("ws:/%s/pepper/initiate", ip.toString());
                } catch (Exception err) {
                    Log.d("setMachineIP", String.format("Failed to parse the IP address: %s, error: %s", IPString, err));
                }
            }
        });

        // other variables
        packageName = getPackageName();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

//    // Pepper RobotLifecycleCallbacks implementation
//
//    @Override
//    public void onRobotFocusGained(QiContext qiContext) {
//        Log.i(TAG, "pepper focus gained");
//        this.qiContext = qiContext;
////        startListen();
//    }
//
//    @Override
//    public void onRobotFocusLost() {
//        Log.i(TAG, "pepper focus lost");
//        this.qiContext = null;
//    }
//
//    @Override
//    public void onRobotFocusRefused(String reason) {
//        Log.i(TAG, "pepper focus refused: " + reason);
//    }
}