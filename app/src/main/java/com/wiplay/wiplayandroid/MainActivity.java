package com.wiplay.wiplayandroid;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import com.onesignal.OSSubscriptionObserver;
import com.onesignal.OSSubscriptionStateChanges;
import com.onesignal.OneSignal;

public class MainActivity extends AppCompatActivity implements OSSubscriptionObserver {
    private WebView mWebView;
    private ImageView cargando;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> mUploadMessage;
    private static final int STORAGE_PERMISSION_CODE = 123;
    private final static int FILECHOOSER_RESULTCODE=1;

    private String URL;
    private String ONESIGNAL_ID;

    private FusedLocationProviderClient locationClient;

    private Boolean paginaCargada = false;

    private Location locationTemporal;

    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private Handler handler = new Handler();
    private Runnable sendLocationRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d("Thread", "CCM - Sending: " + locationTemporal + " - " + paginaCargada);
            if (locationTemporal != null && paginaCargada) {
                sendLocationToWebView(locationTemporal.getLatitude(), locationTemporal.getLongitude());
                // Programa el siguiente envío en una hora
                handler.postDelayed(this, 1000 * 60 * 60);
            } else {
                //Si no hemos enviado, paramos durante 5 segundos solo
                handler.postDelayed(this, 1000 * 5);
            }


        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("onCreate", "CCM - Init");

        URL = getString(R.string.app_url);
        ONESIGNAL_ID = getString(R.string.app_onesignal_id);

        setContentView(R.layout.activity_main);

        //Incorporamos Push con OneSignal
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
        OneSignal.initWithContext(this);
        OneSignal.setAppId(this.ONESIGNAL_ID);
        OneSignal.addSubscriptionObserver(this);

        mWebView = findViewById(R.id.webview);
        cargando = findViewById(R.id.logo);
        progressBar = findViewById(R.id.progressBar);
        String principalColor = getResources().getString(R.string.principal_color);
        String secondaryColor = getResources().getString(R.string.secondary_color);
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(Color.parseColor(secondaryColor)));
        progressBar.setBackgroundColor(Color.parseColor(principalColor));


        //Permitir la descarga de documentos desde WEBVIEW
        mWebView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

                request.setMimeType(mimeType);
                String cookies = CookieManager.getInstance().getCookie(url);
                request.addRequestHeader("cookie", cookies);
                request.addRequestHeader("User-Agent", userAgent);
                request.setDescription("Descarregant fitxer...");
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType));
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);
                Toast.makeText(getApplicationContext(), "Descarregant fitxer", Toast.LENGTH_LONG).show();
                cargando.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
            }
        });

        // Enable Javascript
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; VOG-L29) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.5359.79 Mobile Safari/537.36 OPR/63.3.3216.58675");
        webSettings.setJavaScriptEnabled(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setTextZoom(90);
        webSettings.setAllowFileAccess(true);
        webSettings.setGeolocationEnabled(true);

        CookieManager.getInstance().setAcceptCookie(true);
        if(android.os.Build.VERSION.SDK_INT >= 21) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(mWebView, true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        Intent intent = getIntent();
        String action = intent.getAction();
        if (intent.getData() != null) {
            //Si hemos interceptado alguna petición
            mWebView.loadUrl(intent.getData().toString());
        } else {
            //Sino pintamos el acceso por defecto
            System.out.println("Loading URL: " + this.URL);
            mWebView.loadUrl(this.URL+ "?app=true");
        }

        locationClient = LocationServices.getFusedLocationProviderClient(this);

        configLoader();

        startLocationUpdates();

        handler.post(sendLocationRunnable);
    }

    private void startLocationUpdates() {
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000); // Intervalo de actualización deseado, e.g., 10 segundos
        locationRequest.setFastestInterval(5000); // La tasa más rápida en milisegundos en la que tu aplicación puede manejar actualizaciones
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setSmallestDisplacement(100); // Distancia mínima entre actualizaciones en metros, e.g., 100 metros

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    // Si la página ha cargado y la ubicación ha cambiado, envía la nueva ubicación al WebView
                    if (paginaCargada) {
                        sendLocationToWebView(location.getLatitude(), location.getLongitude());
                    }
                    locationTemporal = location;
                }
            }
        };

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(locationRequest, locationCallback, null /* Looper */);
        }
    }

    private void stopLocationUpdates() {
        LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(locationCallback);
    }

    private void sendLocationToWebView(double lat, double lon) {
        Log.d("web", "CCM - Send location: " + this.paginaCargada);
        final String jsCode = "javascript:updateLatLon(" + lat + "," + lon + ");";
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mWebView.evaluateJavascript(jsCode, null);
            }
        });
    }


    private void configLoader() {
        Log.d("Config", "CCM - Config");
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if(consoleMessage.message().contains("Failed to execute 'postMessage'")) {
                    mWebView.loadUrl(URL + "/prMobileHome?tabAction=profile");
                } else {
                    //Log.d("MyApplication", consoleMessage.message() + " -- From line " + consoleMessage.lineNumber() + " of " + consoleMessage.sourceId());
                }
                return super.onConsoleMessage(consoleMessage);
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                mUploadMessage = filePathCallback;
                requestStoragePermission();

                return true;
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                super.onGeolocationPermissionsShowPrompt(origin, callback);
                // Aquí otorgas el permiso para la geolocalización en el WebView
                Log.d("WebView", "CCM - onGeolocationPermissionsShowPrompt called for origin: " + origin);
                callback.invoke(origin, true, false);
            }
        });
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                cargando.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.VISIBLE);
                if(url.startsWith("https://accounts.google.com")) {
                    return false;
                } else if (url.startsWith("http:") || url.startsWith("https:")) {
                    return false;
                }

                // Otherwise allow the OS to handle things like tel, mailto, etc.
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                cargando.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                paginaCargada = true;

                if(locationTemporal != null) {
                    sendLocationToWebView(locationTemporal.getLatitude(), locationTemporal.getLongitude());
                }

            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if(requestCode==FILECHOOSER_RESULTCODE)
        {
            if (null == mUploadMessage) return;
            Uri result = intent == null || resultCode != RESULT_OK ? null
                    : intent.getData();
            if(result ==null){
                mUploadMessage.onReceiveValue(null);
            }else{
                mUploadMessage.onReceiveValue(new Uri[]{result});
            }
            mUploadMessage = null;
        }
    }

    private void openFileExplorer(){
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        MainActivity.this.startActivityForResult( Intent.createChooser( i, "File Chooser" ), MainActivity.FILECHOOSER_RESULTCODE );
    }

    private void requestStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
            openFileExplorer();
            return;
        }


        if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)) {
            //If the user has denied the permission previously your code will come to this block
            //Here you can explain why you need this permission
            //Explain here why you need this permission
        }
        //And finally ask for the permission
        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
    }

    @Override
    public void onOSSubscriptionChanged(OSSubscriptionStateChanges stateChanges) {
        Intent intent = getIntent();
        String action = intent.getAction();
        String userID = stateChanges.getTo().getUserId();
        if (intent.getData() != null) {
            //Si hemos interceptado alguna petición
            mWebView.loadUrl(intent.getData().toString());
        } else {
            //Sino pintamos el acceso por defecto
            mWebView.loadUrl(URL + "?app=true&userID=" + userID);
        }
    }
}