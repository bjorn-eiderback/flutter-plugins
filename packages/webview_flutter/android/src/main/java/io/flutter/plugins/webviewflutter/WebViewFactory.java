// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.webviewflutter;

import android.content.Context;
import android.view.View;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.StandardMessageCodec;
import io.flutter.plugin.platform.PlatformView;
import io.flutter.plugin.platform.PlatformViewFactory;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import java.util.Map;

public final class WebViewFactory extends PlatformViewFactory {
  private final Registrar registrar;

  WebViewFactory(Registrar registrar) {
    super(StandardMessageCodec.INSTANCE);
    this.registrar = registrar;
  }

  @SuppressWarnings("unchecked")
  @Override
  public PlatformView create(Context context, int id, Object args) {
    Map<String, Object> params = (Map<String, Object>) args;
    return new FlutterWebView(registrar, context, id, params);
  }
}
