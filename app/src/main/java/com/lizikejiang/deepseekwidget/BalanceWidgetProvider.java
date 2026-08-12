package com.lizikejiang.deepseekwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BalanceWidgetProvider extends AppWidgetProvider {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        updateWidgets(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onEnabled(Context context) {
        // 第一个小部件被添加时
    }

    @Override
    public void onDisabled(Context context) {
        // 最后一个小部件被移除时
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(intent.getAction())) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            ComponentName name = new ComponentName(context, BalanceWidgetProvider.class);
            int[] ids = mgr.getAppWidgetIds(name);
            if (ids.length > 0) {
                updateWidgets(context, mgr, ids);
            }
        }
    }

    public static void updateWidgets(Context context, AppWidgetManager mgr, int[] ids) {
        String apiKey = MainActivity.getApiKey(context);

        if (TextUtils.isEmpty(apiKey)) {
            for (int id : ids) {
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
                views.setTextViewText(R.id.tv_balance, "设置");
                views.setTextViewText(R.id.tv_currency, "点击输入 API Key");
                views.setTextViewText(R.id.tv_updated, "");
                views.setOnClickPendingIntent(R.id.widget_root, getOpenAppIntent(context));

                mgr.updateAppWidget(id, views);
            }
            return;
        }

        // 先显示加载中
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
            views.setTextViewText(R.id.tv_balance, "···");
            views.setTextViewText(R.id.tv_currency, "加载中");
            views.setTextViewText(R.id.tv_updated, "");
            views.setOnClickPendingIntent(R.id.widget_root, getOpenAppIntent(context));
            mgr.updateAppWidget(id, views);
        }

        // 后台请求
        executor.submit(() -> {
            BalanceResult result = fetchBalance(apiKey);
            mainHandler.post(() -> {
                for (int id : ids) {
                    RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
                    views.setOnClickPendingIntent(R.id.widget_root, getOpenAppIntent(context));

                    if (result.error != null) {
                        views.setTextViewText(R.id.tv_balance, "出错");
                        views.setTextViewText(R.id.tv_currency, result.error);
                        views.setTextViewText(R.id.tv_updated, "");
                    } else {
                        views.setTextViewText(R.id.tv_balance, result.balance);
                        views.setTextViewText(R.id.tv_currency, result.currency + "  " + result.status);
                        views.setTextViewText(R.id.tv_updated, "更新 " + result.updateTime);
                    }

                    mgr.updateAppWidget(id, views);
                }
            });
        });
    }

    private static PendingIntent getOpenAppIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static BalanceResult fetchBalance(String apiKey) {
        BalanceResult result = new BalanceResult();
        try {
            URL url = new URL("https://api.deepseek.com/user/balance");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int code = conn.getResponseCode();
            if (code == 200) {
                InputStream is = conn.getInputStream();
                java.util.Scanner s = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
                String json = s.hasNext() ? s.next() : "";
                s.close();

                JSONObject data = new JSONObject(json);
                boolean available = data.optBoolean("is_available", true);
                JSONArray infos = data.optJSONArray("balance_infos");

                if (infos != null && infos.length() > 0) {
                    JSONObject primary = infos.getJSONObject(0);
                    result.balance = formatBalance(primary.optString("total_balance", "0"));
                    result.currency = primary.optString("currency", "CNY");
                    result.status = available ? "✓" : "余额不足";
                } else {
                    result.balance = "0.00";
                    result.currency = "CNY";
                    result.status = "无数据";
                }
                result.updateTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
            } else if (code == 401 || code == 403) {
                result.error = "Key 无效";
            } else {
                result.error = "HTTP " + code;
            }
            conn.disconnect();
        } catch (Exception e) {
            result.error = "网络错误";
        }
        return result;
    }

    private static String formatBalance(String val) {
        try {
            double d = Double.parseDouble(val);
            if (d >= 10000) {
                return String.format("%.0f", d);
            } else if (d >= 1000) {
                return String.format("%.1f", d);
            }
            return String.format("%.2f", d);
        } catch (Exception e) {
            return val;
        }
    }

    private static class BalanceResult {
        String balance = "--";
        String currency = "";
        String status = "";
        String updateTime = "";
        String error = null;
    }
}
