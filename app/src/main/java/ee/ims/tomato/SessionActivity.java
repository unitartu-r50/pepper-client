package ee.ims.tomato;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;

import com.aldebaran.qi.sdk.QiSDK;
import com.aldebaran.qi.sdk.design.activity.RobotActivity;

import android.os.IBinder;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SessionActivity extends RobotActivity implements CommunicationService.Callbacks {
    String TAG = "SessionMainActivity";
    String packageName;

    ImageView mainImageView;
    ImageView connectionStatusImageView;
    WebView webView;

    String serverURL;
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    CommunicationService communication;
    Boolean isCommunicationBound;
    ServiceConnection connection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session);

        packageName = getPackageName();
        mainImageView = (ImageView) findViewById(R.id.imageView);
        connectionStatusImageView = (ImageView) findViewById(R.id.connectionStatusImageView);
        webView = (WebView) findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().getLoadsImagesAutomatically();
        webView.getSettings().setAllowContentAccess(true);
        webView.getSettings().setDomStorageEnabled(true);

        // adding a webclient in order to open new links in the same view
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return false;
            }
        });

        // service binding
        serverURL = getIntent().getStringExtra(MainActivity.EXTRA_SERVER_URL);
        connection = new ServiceConnection() {
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
        Intent serviceIntent = new Intent(this, CommunicationService.class);
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
    }

    // CommunicationService callbacks implementation

    @Override
    public void setImage(Bitmap bitmap) {
        runOnUiThread(() -> {
            webView.setVisibility(View.INVISIBLE);
            mainImageView.setImageBitmap(bitmap);
        });
    }

    @Override
    public void setImageResource(Integer resID) {
        runOnUiThread(() -> {
            webView.setVisibility(View.INVISIBLE);
            mainImageView.setImageResource(resID);
        });
    }

    @Override
    public void loadURL(String uri) {
        runOnUiThread(() -> {
            webView.setVisibility(View.VISIBLE);
            webView.loadUrl(uri);
//            webView.postDelayed(() -> webView.loadUrl(uri), 500);
        });
    }

    @Override
    public void setConnectionStatus(Boolean isConnectionUp) {
        int resID;
        if (isConnectionUp) {
            resID = R.drawable.ic_status_on_small;
        } else {
            resID = R.drawable.ic_status_off_small;
        }
        runOnUiThread(() -> connectionStatusImageView.setImageResource(resID));
    }
}