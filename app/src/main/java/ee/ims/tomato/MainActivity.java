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
    public static String EXTRA_SERVER_URL = "ee.ims.tomato.MainActivity.serverURL";

    String TAG = "MainActivity";
    String packageName;

    EditText machineIPInput;
    Button startButton;
    Button setIPButton;
    String serverURL = "ws://10.0.2.2:8080/pepper/initiate";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        packageName = getPackageName();
        machineIPInput = findViewById(R.id.editMachineIP);

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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}