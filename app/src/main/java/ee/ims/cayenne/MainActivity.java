package ee.ims.cayenne;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.aldebaran.qi.sdk.design.activity.RobotActivity;

import java.net.InetSocketAddress;

public class MainActivity extends RobotActivity {
    public static String EXTRA_SERVER_URL = "ee.ims.cayenne.MainActivity.serverURL";
    private static String PREVIOUS_IP_KEY = "ee.ims.cayenne.MainActivity.preferences.previous_IP";

    String TAG = "MainActivity";
    String packageName;

    EditText machineIPInput;
    Button startButton;
    String serverURL = "ws://10.0.2.2:8080/api/pepper/initiate";

    SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        packageName = getPackageName();
        machineIPInput = findViewById(R.id.editMachineIP);
        sharedPreferences = this.getPreferences(Context.MODE_PRIVATE);
        startButton = findViewById(R.id.startButton);

        String IPString = sharedPreferences.getString(PREVIOUS_IP_KEY, null);
        if (IPString != null) {
            serverURL = composeAPIURI(IPString);
            if (serverURL != null) {
                machineIPInput.setText(IPString);
                startButton.setBackgroundColor(getResources().getColor(R.color.colorAccent));
            }
        }

        startButton.setOnClickListener(new View.OnClickListener() {
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

                Intent activityIntent = new Intent(getApplicationContext(), SessionActivity.class);
                activityIntent.putExtra(EXTRA_SERVER_URL, serverURL);
                startActivity(activityIntent);
            }
        });
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