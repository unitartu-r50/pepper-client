package ee.ims.tomato;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
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
    private static String PREVIOUS_IP_KEY = "ee.ims.tomato.MainActivity.preferences.previous_IP";

    String TAG = "MainActivity";
    String packageName;

    EditText machineIPInput;
    Button startButton;
    Button setIPButton;
    String serverURL = "ws://10.0.2.2:8080/api/pepper/initiate";

    SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        packageName = getPackageName();
        machineIPInput = findViewById(R.id.editMachineIP);
        sharedPreferences = this.getPreferences(Context.MODE_PRIVATE);
        startButton = findViewById(R.id.start);
        setIPButton = findViewById(R.id.setIPButton);

        String IPString = sharedPreferences.getString(PREVIOUS_IP_KEY, null);
        if (IPString != null) {
            serverURL = composeAPIURI(IPString);
            if (serverURL != null) {
                machineIPInput.setText(IPString);
                startButton.setBackgroundColor(getResources().getColor(R.color.colorAccent));
            }
        }
        // start Session activity, if the preferred IP existed in preferences
//        if (IPString != null) {
//            Intent activityIntent = new Intent(getApplicationContext(), SessionActivity.class);
//            activityIntent.putExtra(EXTRA_SERVER_URL, serverURL);
//            startActivity(activityIntent);
//        } // otherwise, wait for a user to enter a new IP

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent activityIntent = new Intent(getApplicationContext(), SessionActivity.class);
                activityIntent.putExtra(EXTRA_SERVER_URL, serverURL);
                startActivity(activityIntent);
            }
        });

        setIPButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String IPString = machineIPInput.getText().toString();
                serverURL = composeAPIURI(IPString);
                // save the given IP as a preference to not enter it the next time
                if (serverURL != null) {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(PREVIOUS_IP_KEY, IPString);
                    editor.apply();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private String composeAPIURI(String ipString) {
        String uri = null;
        try {
            InetSocketAddress ip = new InetSocketAddress(ipString, 8080);
            uri = String.format("ws:/%s/api/pepper/initiate", ip.toString());
        } catch (Exception err) {
            Log.d("setMachineIP", String.format("Failed to parse the IP address: %s, error: %s", ipString, err));
        }
        return uri;
    }
}