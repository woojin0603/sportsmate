package kr.or.sportmap.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.net.URI;

public class MainActivity extends Activity {
  private static final String PREFS = "sportmap_mobile";
  private static final String SERVER_URL = "server_url";
  private static final int FILE_CHOOSER_REQUEST = 4102;
  private static final String DEFAULT_URL = "http://10.0.2.2:8080";

  private WebView webView;
  private ProgressBar progressBar;
  private LinearLayout errorPanel;
  private TextView errorMessage;
  private ValueCallback<Uri[]> fileCallback;
  private SharedPreferences preferences;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
    setContentView(createContentView());
    configureWebView();
    if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) return;
    String savedUrl = preferences.getString(SERVER_URL, "");
    if (savedUrl.isBlank()) {
      showServerDialog(true);
    } else {
      loadServer(savedUrl);
    }
  }

  private View createContentView() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.rgb(247, 248, 245));

    LinearLayout bar = new LinearLayout(this);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setPadding(dp(18), dp(8), dp(10), dp(8));
    bar.setBackgroundColor(Color.rgb(23, 62, 50));

    TextView title = new TextView(this);
    title.setText("SportMap");
    title.setTextColor(Color.WHITE);
    title.setTextSize(20);
    title.setTypeface(null, android.graphics.Typeface.BOLD);
    bar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));

    Button settings = new Button(this);
    settings.setText("서버 설정");
    settings.setTextColor(Color.rgb(23, 62, 50));
    settings.setTextSize(12);
    settings.setAllCaps(false);
    settings.setOnClickListener(view -> showServerDialog(false));
    bar.addView(settings, new LinearLayout.LayoutParams(dp(96), dp(42)));
    root.addView(bar, new LinearLayout.LayoutParams(-1, dp(64)));

    FrameLayout browser = new FrameLayout(this);
    webView = new WebView(this);
    browser.addView(webView, new FrameLayout.LayoutParams(-1, -1));
    progressBar = new ProgressBar(this);
    FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(dp(48), dp(48));
    progressParams.gravity = Gravity.CENTER;
    browser.addView(progressBar, progressParams);

    errorPanel = new LinearLayout(this);
    errorPanel.setOrientation(LinearLayout.VERTICAL);
    errorPanel.setGravity(Gravity.CENTER);
    errorPanel.setPadding(dp(28), dp(28), dp(28), dp(28));
    errorPanel.setBackgroundColor(Color.rgb(247, 248, 245));
    errorPanel.setVisibility(View.GONE);

    errorMessage = new TextView(this);
    errorMessage.setText("서버에 연결할 수 없습니다.\nPC와 휴대폰의 네트워크 및 Spring Boot 실행 상태를 확인해 주세요.");
    errorMessage.setTextColor(Color.rgb(44, 55, 50));
    errorMessage.setTextSize(17);
    errorMessage.setGravity(Gravity.CENTER);
    errorMessage.setLineSpacing(0, 1.25f);
    errorPanel.addView(errorMessage, new LinearLayout.LayoutParams(-1, -2));

    Button retry = new Button(this);
    retry.setText("다시 연결");
    retry.setAllCaps(false);
    retry.setOnClickListener(view -> retryServer());
    LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(dp(150), dp(50));
    retryParams.topMargin = dp(20);
    errorPanel.addView(retry, retryParams);
    browser.addView(errorPanel, new FrameLayout.LayoutParams(-1, -1));

    root.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));
    return root;
  }

  private void configureWebView() {
    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setDatabaseEnabled(true);
    settings.setAllowFileAccess(false);
    settings.setAllowContentAccess(true);
    settings.setMediaPlaybackRequiresUserGesture(false);
    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    settings.setSupportMultipleWindows(true);
    settings.setJavaScriptCanOpenWindowsAutomatically(true);
    settings.setUserAgentString(settings.getUserAgentString() + " SportMapAndroid/0.1");

    CookieManager cookies = CookieManager.getInstance();
    cookies.setAcceptCookie(true);
    cookies.setAcceptThirdPartyCookies(webView, true);

    webView.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return openOutsideWhenNeeded(request.getUrl());
      }

      @Override
      public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
        hideConnectionError();
        progressBar.setVisibility(View.VISIBLE);
      }

      @Override
      public void onPageFinished(WebView view, String url) {
        progressBar.setVisibility(View.GONE);
        CookieManager.getInstance().flush();
      }

      @Override
      public void onReceivedError(
          WebView view,
          WebResourceRequest request,
          WebResourceError error
      ) {
        if (request.isForMainFrame()) {
          showConnectionError("서버에 연결할 수 없습니다.\nPC와 휴대폰의 네트워크 및 Spring Boot 실행 상태를 확인해 주세요.");
        }
      }
    });

    webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
        openExternal(Uri.parse(url))
    );

    webView.setWebChromeClient(new WebChromeClient() {
      @Override
      public void onProgressChanged(WebView view, int progress) {
        progressBar.setVisibility(progress < 100 ? View.VISIBLE : View.GONE);
      }

      @Override
      public boolean onCreateWindow(
          WebView view,
          boolean isDialog,
          boolean isUserGesture,
          Message resultMsg
      ) {
        WebView popup = new WebView(MainActivity.this);
        popup.setWebViewClient(new WebViewClient() {
          @Override
          public boolean shouldOverrideUrlLoading(WebView child, WebResourceRequest request) {
            openExternal(request.getUrl());
            child.destroy();
            return true;
          }
        });
        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        transport.setWebView(popup);
        resultMsg.sendToTarget();
        return true;
      }

      @Override
      public boolean onShowFileChooser(
          WebView view,
          ValueCallback<Uri[]> callback,
          FileChooserParams params
      ) {
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        fileCallback = callback;
        Intent intent = params.createIntent();
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
          startActivityForResult(intent, FILE_CHOOSER_REQUEST);
          return true;
        } catch (Exception error) {
          fileCallback = null;
          Toast.makeText(MainActivity.this, "파일 선택기를 열 수 없습니다.", Toast.LENGTH_SHORT).show();
          return false;
        }
      }
    });
  }

  private void showServerDialog(boolean firstRun) {
    EditText input = new EditText(this);
    input.setSingleLine(true);
    input.setText(preferences.getString(SERVER_URL, DEFAULT_URL));
    input.setSelection(input.getText().length());
    int padding = dp(22);
    FrameLayout holder = new FrameLayout(this);
    holder.setPadding(padding, 0, padding, 0);
    holder.addView(input, new FrameLayout.LayoutParams(-1, dp(58)));

    AlertDialog dialog = new AlertDialog.Builder(this)
        .setTitle("Spring Boot 서버 주소")
        .setMessage("에뮬레이터: http://10.0.2.2:8080\n실제 휴대폰: http://PC의_IP:8080")
        .setView(holder)
        .setPositiveButton("연결", null)
        .setNegativeButton(firstRun ? "종료" : "취소", (target, which) -> {
          if (firstRun) finish();
        })
        .create();
    dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
      String normalized = normalizeUrl(input.getText().toString());
      if (normalized == null) {
        input.setError("http:// 또는 https://로 시작하는 주소를 입력하세요.");
        return;
      }
      preferences.edit().putString(SERVER_URL, normalized).apply();
      dialog.dismiss();
      loadServer(normalized);
    }));
    dialog.setCanceledOnTouchOutside(!firstRun);
    dialog.show();
  }

  private String normalizeUrl(String value) {
    try {
      String trimmed = value.trim();
      while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
      URI uri = URI.create(trimmed);
      if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) return null;
      if (uri.getHost() == null) return null;
      return trimmed;
    } catch (Exception ignored) {
      return null;
    }
  }

  private void loadServer(String serverUrl) {
    hideConnectionError();
    progressBar.setVisibility(View.VISIBLE);
    webView.loadUrl(serverUrl);
  }

  /** 현재 설정된 SportMap 서버만 앱 안에서 열고 외부 사이트는 기본 브라우저로 연다. */
  private boolean openOutsideWhenNeeded(Uri uri) {
    String scheme = uri.getScheme();
    if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
      openExternal(uri);
      return true;
    }
    Uri server = Uri.parse(preferences.getString(SERVER_URL, DEFAULT_URL));
    boolean sameServer = java.util.Objects.equals(server.getHost(), uri.getHost()) &&
        effectivePort(server) == effectivePort(uri);
    if (sameServer) return false;
    openExternal(uri);
    return true;
  }

  /** 외부 주소를 처리할 수 있는 기본 앱으로 전달한다. */
  private void openExternal(Uri uri) {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, uri));
    } catch (Exception ignored) {
      Toast.makeText(this, "연결된 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
    }
  }

  /** URI에 포트가 생략됐을 때 HTTP/HTTPS 기본 포트를 계산한다. */
  private int effectivePort(Uri uri) {
    if (uri.getPort() >= 0) return uri.getPort();
    return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
  }

  /** 연결 오류 화면을 표시하고 로딩 표시를 종료한다. */
  private void showConnectionError(String message) {
    progressBar.setVisibility(View.GONE);
    errorMessage.setText(message);
    errorPanel.setVisibility(View.VISIBLE);
  }

  /** 페이지를 다시 요청하기 전에 연결 오류 화면을 감춘다. */
  private void hideConnectionError() {
    if (errorPanel != null) errorPanel.setVisibility(View.GONE);
  }

  /** 저장된 서버 주소를 다시 불러와 연결을 재시도한다. */
  private void retryServer() {
    String serverUrl = preferences.getString(SERVER_URL, DEFAULT_URL);
    loadServer(serverUrl);
  }

  @Override
  @SuppressWarnings("deprecation")
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode != FILE_CHOOSER_REQUEST || fileCallback == null) return;
    fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
    fileCallback = null;
  }

  @Override
  public void onBackPressed() {
    if (webView.canGoBack()) {
      webView.goBack();
    } else {
      super.onBackPressed();
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    webView.saveState(outState);
    super.onSaveInstanceState(outState);
  }

  @Override
  protected void onDestroy() {
    if (webView != null) {
      webView.stopLoading();
      webView.destroy();
    }
    super.onDestroy();
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
