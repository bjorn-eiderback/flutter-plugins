package io.flutter.plugins.webviewflutter;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;

import java.util.HashMap;
import java.util.Map;

import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

public class FlutterWebChromeClient extends WebChromeClient {

  protected static final String LOG_TAG = "FlutterWebViewClient";
  private FlutterWebView flutterWebView;
  private final MethodChannel methodChannel;

  private ValueCallback<Uri[]> mUploadMessageArray;
  private ValueCallback<Uri> mUploadMessage;
  private final static int FILECHOOSER_RESULTCODE = 1;

  public FlutterWebChromeClient(Object obj, MethodChannel methodChannel) {
    super();
    if (obj instanceof FlutterWebView)
	    this.flutterWebView = (FlutterWebView) obj;
	  this.methodChannel = methodChannel;
  }

  @Override
  public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
    Map<String, Object> obj = new HashMap<>();
    obj.put("sourceURL", consoleMessage.sourceId());
    obj.put("lineNumber", consoleMessage.lineNumber());
    obj.put("message", consoleMessage.message());
    obj.put("messageLevel", consoleMessage.messageLevel().toString());
    this.methodChannel.invokeMethod("onConsoleMessage", obj);
	  return true;
  }

  @Override
  public void onProgressChanged(WebView view, int progress) {
    Map<String, Object> obj = new HashMap<>();
    obj.put("progress", progress);
	  this.methodChannel.invokeMethod("onProgressChanged", obj);

    super.onProgressChanged(view, progress);
  }

  @Override
  public void onReceivedTitle(WebView view, String title) {
    super.onReceivedTitle(view, title);
	
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      if (title.contains("404") || title.contains("500") || title.contains("Error")) {
        Map<String, Object> obj = new HashMap<>();
        obj.put("url", "Unknown url");
        obj.put("statusCode", 400);
        obj.put("message", "title contain 404 or 500 or Error");
        this.methodChannel.invokeMethod("onHttpError", obj);
      }
    }
  }

  //The undocumented magic method override
  //Eclipse will swear at you if you try to put @Override here
  // For Android 3.0+
  public void openFileChooser(ValueCallback<Uri> uploadMsg) {

    mUploadMessage = uploadMsg;
    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("image/*");
    flutterWebView.activity.startActivityForResult(Intent.createChooser(i, "File Chooser"), FILECHOOSER_RESULTCODE);
  }

  // For Android 3.0+
  public void openFileChooser(ValueCallback uploadMsg, String acceptType) {
    mUploadMessage = uploadMsg;
    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("*/*");
    flutterWebView.activity.startActivityForResult(
            Intent.createChooser(i, "File Browser"),
            FILECHOOSER_RESULTCODE);
  }

  //For Android 4.1
  public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
    mUploadMessage = uploadMsg;
    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("image/*");
    flutterWebView.activity.startActivityForResult(Intent.createChooser(i, "File Chooser"), FILECHOOSER_RESULTCODE);

  }

  //For Android 5.0+
  public boolean onShowFileChooser(
          WebView webView, ValueCallback<Uri[]> filePathCallback,
          FileChooserParams fileChooserParams) {
    if (mUploadMessageArray != null) {
      mUploadMessageArray.onReceiveValue(null);
    }
    mUploadMessageArray = filePathCallback;

    Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
    contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
    contentSelectionIntent.setType("*/*");
    Intent[] intentArray;
    intentArray = new Intent[0];

    Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
    chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
    chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser");
    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
    flutterWebView.activity.startActivityForResult(chooserIntent, FILECHOOSER_RESULTCODE);
    return true;
  }
  

}
