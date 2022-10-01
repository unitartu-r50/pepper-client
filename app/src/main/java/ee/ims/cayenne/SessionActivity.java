package ee.ims.cayenne;

import static java.lang.String.valueOf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;

import com.aldebaran.qi.sdk.QiSDK;
import com.aldebaran.qi.sdk.design.activity.RobotActivity;
import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayerSupportFragment;

import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.fragment.app.FragmentTransaction;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SessionActivity extends RobotActivity implements CommunicationService.Callbacks {
    String TAG = "SessionMainActivity";
    String packageName;

    ImageView mainImageView;
    ImageView connectionStatusImageView;
    WebView webView;
    YouTubePlayerSupportFragment youTubePlayerFragment;
    AuthFragment authFragment;

    String serverURL;
    String videoID;
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    CommunicationService communication;
    Boolean isCommunicationBound;
    ServiceConnection connection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (!isTaskRoot()
            && getIntent().hasCategory(Intent.CATEGORY_LAUNCHER)
            && getIntent().getAction() != null
            && getIntent().getAction().equals(Intent.ACTION_MAIN)) {

            finish();
            return;
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session);

        packageName = getPackageName();
        mainImageView = (ImageView) findViewById(R.id.imageView);
        connectionStatusImageView = (ImageView) findViewById(R.id.connectionStatusImageView);

        // initializing WebView and making it invisible from the very beginning,
        // because it takes the whole screen
        webView = (WebView) findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().getLoadsImagesAutomatically();
        webView.getSettings().setAllowContentAccess(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setVisibility(View.INVISIBLE);
        // adding a web view client in order to open new links in the same view
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return false;
            }
        });
        // adding a web chrome client in order to allow full screen video
        webView.setWebChromeClient(new FullScreenVideoChromeClient());

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
            if (uri.startsWith("youtube:")) {

                videoID = uri.replace("youtube:", "");

                youTubePlayerFragment = YouTubePlayerSupportFragment.newInstance();
                youTubePlayerFragment.initialize("AIzaSyCNem-D_zKcygVu70qmba6qW5sTtlg8opY", new YouTubePlayer.OnInitializedListener() {
                    @Override
                    public void onInitializationSuccess(YouTubePlayer.Provider provider, YouTubePlayer player, boolean wasRestored) {

                        YouTubePlayer.PlayerStateChangeListener stateChangeListener = new YouTubePlayer.PlayerStateChangeListener() {
                            @Override
                            public void onLoading() {

                            }

                            @Override
                            public void onLoaded(String s) {

                            }

                            @Override
                            public void onAdStarted() {

                            }

                            @Override
                            public void onVideoStarted() {

                            }

                            @Override
                            public void onVideoEnded() {
                                Log.d(TAG, "Video finished");
                                getSupportFragmentManager().beginTransaction().remove((androidx.fragment.app.Fragment) (Object) youTubePlayerFragment).commit();
                            }

                            @Override
                            public void onError(YouTubePlayer.ErrorReason errorReason) {

                            }
                        };

                        player.loadVideo(videoID);
                        player.setFullscreen(true);
                        player.setPlayerStateChangeListener(stateChangeListener);
                        player.play();

                    }

                    @Override
                    public void onInitializationFailure(YouTubePlayer.Provider provider, YouTubeInitializationResult youTubeInitializationResult) {

                    }
                });

                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.youtube_fragment, (androidx.fragment.app.Fragment) (Object) youTubePlayerFragment).commit();

            }
            else {
                webView.setVisibility(View.VISIBLE);
                webView.loadUrl(uri);
            }
        });
    }

    public void clearFragment() {
        Log.d(TAG, "ClearFragment");
        if (youTubePlayerFragment != null) {
            getSupportFragmentManager().beginTransaction().remove((androidx.fragment.app.Fragment) (Object) youTubePlayerFragment).commit();
        }
        if (authFragment != null) {
            authFragment.dismiss();
        }
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

    private class FullScreenVideoChromeClient extends WebChromeClient {
        private View mCustomView;
        private WebChromeClient.CustomViewCallback mCustomViewCallback;
        protected FrameLayout mFullscreenContainer;
        private int mOriginalOrientation;
        private int mOriginalSystemUiVisibility;
        private static final int FULL_SCREEN_SETTING = View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE;

        FullScreenVideoChromeClient() {}

        public Bitmap getDefaultVideoPoster() {
            return Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        }

        public void onHideCustomView()
        {
            ((FrameLayout)getWindow().getDecorView()).removeView(this.mCustomView);
            this.mCustomView = null;
            getWindow().getDecorView().setSystemUiVisibility(this.mOriginalSystemUiVisibility);
            setRequestedOrientation(this.mOriginalOrientation);
            this.mCustomViewCallback.onCustomViewHidden();
            this.mCustomViewCallback = null;
        }

        public void onShowCustomView(View paramView, WebChromeClient.CustomViewCallback paramCustomViewCallback)
        {
            if (this.mCustomView != null)
            {
                onHideCustomView();
                return;
            }
            this.mCustomView = paramView;
            this.mOriginalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
            this.mOriginalOrientation = getRequestedOrientation();
            this.mCustomViewCallback = paramCustomViewCallback;
            ((FrameLayout)getWindow().getDecorView()).addView(this.mCustomView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            getWindow().getDecorView().setSystemUiVisibility(FULL_SCREEN_SETTING);
        }
    }

    public void auth(String auth_code, String helptext) {
        if (authFragment != null) {
            authFragment.dismiss();
        }
        Log.d(TAG, valueOf(authFragment != null));
        authFragment = AuthFragment.newInstance(auth_code, helptext);
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        authFragment.show(ft, "Auth_dialog");
        Log.d(TAG, "AUTH CODE: " + auth_code);
    }
}