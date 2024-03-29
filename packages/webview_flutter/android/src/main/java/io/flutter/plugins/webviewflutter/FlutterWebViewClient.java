// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.webviewflutter;

import android.annotation.TargetApi;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebResourceResponse;
import androidx.webkit.WebResourceErrorCompat;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.SslErrorHandler;
import 	android.net.http.SslError;
import androidx.annotation.NonNull;
import androidx.webkit.WebViewClientCompat;
import io.flutter.plugin.common.MethodChannel;
import java.util.HashMap;
import java.util.Map;
import android.content.Intent;
import android.net.Uri;

// We need to use WebViewClientCompat to get
// shouldOverrideUrlLoading(WebView view, WebResourceRequest request)
// invoked by the webview on older Android devices, without it pages that use iframes will
// be broken when a navigationDelegate is set on Android version earlier than N.
class FlutterWebViewClient {
  private static final String TAG = "FlutterWebViewClient";
  private final MethodChannel methodChannel;
  private boolean hasNavigationDelegate;
  private FlutterWebView flutterWebView;

  FlutterWebViewClient(MethodChannel methodChannel, FlutterWebView flutterWebView) {
    this.methodChannel = methodChannel;
    this.flutterWebView = flutterWebView;
  }

  boolean processTelSmsMail(String url) {
    if (url.startsWith(WebView.SCHEME_TEL)) {
      try {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse(url));
        this.flutterWebView.activity.startActivity(intent);
        return true;
      } catch (android.content.ActivityNotFoundException e) {
        Log.e(TAG, "Error dialing " + url + ": " + e.toString());
      }
    } else if (url.startsWith("geo:") || url.startsWith(WebView.SCHEME_MAILTO) || url.startsWith("market:") || url.startsWith("intent:")) {
      try {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        this.flutterWebView.activity.startActivity(intent);
        return true;
      } catch (android.content.ActivityNotFoundException e) {
        Log.e(TAG, "Error with " + url + ": " + e.toString());
      }
    }
    // If sms:5551212?body=This is the message
    else if (url.startsWith("sms:")) {
      try {
        Intent intent = new Intent(Intent.ACTION_VIEW);

        // Get address
        String address;
        int parmIndex = url.indexOf('?');
        if (parmIndex == -1) {
          address = url.substring(4);
        } else {
          address = url.substring(4, parmIndex);

          // If body, then set sms body
          Uri uri = Uri.parse(url);
          String query = uri.getQuery();
          if (query != null) {
            if (query.startsWith("body=")) {
              intent.putExtra("sms_body", query.substring(5));
            }
          }
        }
        intent.setData(Uri.parse("sms:" + address));
        intent.putExtra("address", address);
        intent.setType("vnd.android-dir/mms-sms");
        this.flutterWebView.activity.startActivity(intent);
        return true;
      } catch (android.content.ActivityNotFoundException e) {
        Log.e(TAG, "Error sending sms " + url + ":" + e.toString());
      }
    }

    return false;
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
    if (processTelSmsMail(request.getUrl().toString())) {
      return true;
    }

    if (!hasNavigationDelegate) {
      return false;
    }
    notifyOnNavigationRequest(
        request.getUrl().toString(), request.getRequestHeaders(), view, request.isForMainFrame());
    // We must make a synchronous decision here whether to allow the navigation or not,
    // if the Dart code has set a navigation delegate we want that delegate to decide whether
    // to navigate or not, and as we cannot get a response from the Dart delegate synchronously we
    // return true here to block the navigation, if the Dart delegate decides to allow the
    // navigation the plugin will later make an addition loadUrl call for this url.
    //
    // Since we cannot call loadUrl for a subframe, we currently only allow the delegate to stop
    // navigations that target the main frame, if the request is not for the main frame
    // we just return false to allow the navigation.
    //
    // For more details see: https://github.com/flutter/flutter/issues/25329#issuecomment-464863209
    return request.isForMainFrame();
  }

  private boolean shouldOverrideUrlLoading(WebView view, String url) {
    if (processTelSmsMail(url)) {
      return true;
    }

    if (!hasNavigationDelegate) {
      return false;
    }
    // This version of shouldOverrideUrlLoading is only invoked by the webview on devices with
    // webview versions  earlier than 67(it is also invoked when hasNavigationDelegate is false).
    // On these devices we cannot tell whether the navigation is targeted to the main frame or not.
    // We proceed assuming that the navigation is targeted to the main frame. If the page had any
    // frames they will be loaded in the main frame instead.
    Log.w(
        TAG,
        "Using a navigationDelegate with an old webview implementation, pages with frames or iframes will not work");
    notifyOnNavigationRequest(url, null, view, true);
    return true;
  }

  private void onPageFinished(WebView view, String url) {
    Map<String, Object> args = new HashMap<>();
    args.put("url", url);
    methodChannel.invokeMethod("onPageFinished", args);
  }

  private void onLoadError(WebView view, String url, int code, String msg) {
    Map<String, Object> obj = new HashMap<>();
    obj.put("url", url);
    obj.put("code", code);
    obj.put("message", msg);
    methodChannel.invokeMethod("onLoadError", obj);
  }

  private void onHttpError(WebView view, String url, int statusCode, String msg) {
    Map<String, Object> obj = new HashMap<>();
    obj.put("url", url);
    obj.put("statusCode", statusCode);
    obj.put("message", msg);
    methodChannel.invokeMethod("onHttpError", obj);
  }

  private void notifyOnNavigationRequest(
      String url, Map<String, String> headers, WebView webview, boolean isMainFrame) {
    HashMap<String, Object> args = new HashMap<>();
    args.put("url", url);
    args.put("isForMainFrame", isMainFrame);
    if (isMainFrame) {
      methodChannel.invokeMethod(
          "navigationRequest", args, new OnNavigationRequestResult(url, headers, webview));
    } else {
      methodChannel.invokeMethod("navigationRequest", args);
    }
  }

  // This method attempts to avoid using WebViewClientCompat due to bug
  // https://bugs.chromium.org/p/chromium/issues/detail?id=925887. Also, see
  // https://github.com/flutter/flutter/issues/29446.
  WebViewClient createWebViewClient(boolean hasNavigationDelegate) {
    this.hasNavigationDelegate = hasNavigationDelegate;

    if (!hasNavigationDelegate || android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      return internalCreateWebViewClient();
    }

    return internalCreateWebViewClientCompat();
  }

  private WebViewClient internalCreateWebViewClient() {
    return new WebViewClient() {
      @TargetApi(Build.VERSION_CODES.N)
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return FlutterWebViewClient.this.shouldOverrideUrlLoading(view, request);
      }

      @Override
      public void onPageFinished(WebView view, String url) {
        FlutterWebViewClient.this.onPageFinished(view, url);
      }

      @Override
      public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
        // Deliberately empty. Occasionally the webview will mark events as having failed to be
        // handled even though they were handled. We don't want to propagate those as they're not
        // truly lost.
      }

      @Override
      public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        super.onReceivedError(view, errorCode, description, failingUrl);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
          return;
        }
        FlutterWebViewClient.this.onLoadError(view, failingUrl, errorCode, description);
      }

      @TargetApi(Build.VERSION_CODES.M)
      @Override
      public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        super.onReceivedError(view, request, error);
        if(request.isForMainFrame()) {
          FlutterWebViewClient.this.onLoadError(view, request.getUrl().toString(), 
            error.getErrorCode(), error.getDescription().toString());
        }
      }

      @TargetApi(Build.VERSION_CODES.M)
      @Override
      public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
        super.onReceivedHttpError(view, request, errorResponse);
        if(request.isForMainFrame()) {
          FlutterWebViewClient.this.onHttpError(view, request.getUrl().toString(),
            errorResponse.getStatusCode(), errorResponse.getReasonPhrase());
        }
      }

      @Override
      public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        super.onReceivedSslError(view, handler, error);

        String message;
        switch (error.getPrimaryError()) {
          case SslError.SSL_DATE_INVALID:
            message = "The date of the certificate is invalid";
            break;
          case SslError.SSL_EXPIRED:
            message = "The certificate has expired";
            break;
          case SslError.SSL_IDMISMATCH:
            message = "Hostname mismatch";
            break;
          default:
          case SslError.SSL_INVALID:
            message = "A generic error occurred";
            break;
          case SslError.SSL_NOTYETVALID:
            message = "The certificate is not yet valid";
            break;
          case SslError.SSL_UNTRUSTED:
            message = "The certificate authority is not trusted";
            break;
        }
        FlutterWebViewClient.this.onLoadError(view, error.getUrl(), error.getPrimaryError(), "SslError: " + message);

        handler.cancel();
      }
    };
  }

  private WebViewClientCompat internalCreateWebViewClientCompat() {
    return new WebViewClientCompat() {
      @Override
      public boolean shouldOverrideUrlLoading(
          @NonNull WebView view, @NonNull WebResourceRequest request) {
        return FlutterWebViewClient.this.shouldOverrideUrlLoading(view, request);
      }

      @Override
      public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return FlutterWebViewClient.this.shouldOverrideUrlLoading(view, url);
      }

      @Override
      public void onPageFinished(WebView view, String url) {
        FlutterWebViewClient.this.onPageFinished(view, url);
      }

      @Override
      public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
        // Deliberately empty. Occasionally the webview will mark events as having failed to be
        // handled even though they were handled. We don't want to propagate those as they're not
        // truly lost.
      }

      @Override
      public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        super.onReceivedError(view, errorCode, description, failingUrl);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
          return;
        }
        FlutterWebViewClient.this.onLoadError(view, failingUrl, errorCode, description);
      }

      @TargetApi(Build.VERSION_CODES.M)
      @Override
      public void onReceivedError(WebView view, WebResourceRequest request, WebResourceErrorCompat error) {
        super.onReceivedError(view, request, error);
        if(request.isForMainFrame()) {
          FlutterWebViewClient.this.onLoadError(view, request.getUrl().toString(), 
            error.getErrorCode(), error.getDescription().toString());
        }
      }

      @TargetApi(Build.VERSION_CODES.M)
      @Override
      public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
        super.onReceivedHttpError(view, request, errorResponse);
        if(request.isForMainFrame()) {
          FlutterWebViewClient.this.onHttpError(view, request.getUrl().toString(),
            errorResponse.getStatusCode(), errorResponse.getReasonPhrase());
        }
      }

      @Override
      public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        super.onReceivedSslError(view, handler, error);

        String message;
        switch (error.getPrimaryError()) {
          case SslError.SSL_DATE_INVALID:
            message = "The date of the certificate is invalid";
            break;
          case SslError.SSL_EXPIRED:
            message = "The certificate has expired";
            break;
          case SslError.SSL_IDMISMATCH:
            message = "Hostname mismatch";
            break;
          default:
          case SslError.SSL_INVALID:
            message = "A generic error occurred";
            break;
          case SslError.SSL_NOTYETVALID:
            message = "The certificate is not yet valid";
            break;
          case SslError.SSL_UNTRUSTED:
            message = "The certificate authority is not trusted";
            break;
        }
        FlutterWebViewClient.this.onLoadError(view, error.getUrl(), error.getPrimaryError(), "SslError: " + message);

        handler.cancel();
      }
    };
  }

  private static class OnNavigationRequestResult implements MethodChannel.Result {
    private final String url;
    private final Map<String, String> headers;
    private final WebView webView;

    private OnNavigationRequestResult(String url, Map<String, String> headers, WebView webView) {
      this.url = url;
      this.headers = headers;
      this.webView = webView;
    }

    @Override
    public void success(Object shouldLoad) {
      Boolean typedShouldLoad = (Boolean) shouldLoad;
      if (typedShouldLoad) {
        loadUrl();
      }
    }

    @Override
    public void error(String errorCode, String s1, Object o) {
      throw new IllegalStateException("navigationRequest calls must succeed");
    }

    @Override
    public void notImplemented() {
      throw new IllegalStateException(
          "navigationRequest must be implemented by the webview method channel");
    }

    private void loadUrl() {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        webView.loadUrl(url, headers);
      } else {
        webView.loadUrl(url);
      }
    }
  }
}
