package com.lizikejiang.deepseekwidget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "deepseek_widget_prefs";
    private static final String KEY_API_KEY = "api_key";

    private TextInputEditText etApiKey;
    private MaterialButton btnSave;
    private TextView tvHint;
    private TextView tvDelete;
    private View previewSection;
    private TextView tvPreviewBalance;
    private TextView tvPreviewStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etApiKey = findViewById(R.id.et_api_key);
        btnSave = findViewById(R.id.btn_save);
        tvHint = findViewById(R.id.tv_hint);
        tvDelete = findViewById(R.id.tv_delete);
        previewSection = findViewById(R.id.preview_section);
        tvPreviewBalance = findViewById(R.id.tv_preview_balance);
        tvPreviewStatus = findViewById(R.id.tv_preview_status);

        // 加载已保存的 API Key
        String savedKey = getApiKey(this);
        if (!savedKey.isEmpty()) {
            etApiKey.setText(savedKey);
            previewSection.setVisibility(View.VISIBLE);
            fetchBalancePreview(savedKey);
        }

        btnSave.setOnClickListener(v -> {
            String key = etApiKey.getText().toString().trim();
            if (key.isEmpty()) {
                Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!key.startsWith("sk-")) {
                Toast.makeText(this, "API Key 格式不正确，应以 sk- 开头", Toast.LENGTH_SHORT).show();
                return;
            }
            saveApiKey(this, key);
            tvHint.setText("API Key 已保存");
            tvHint.setTextColor(Color.parseColor("#10B981"));
            previewSection.setVisibility(View.VISIBLE);
            fetchBalancePreview(key);

            // 更新所有桌面小部件
            updateAllWidgets();
        });

        tvDelete.setOnClickListener(v -> {
            saveApiKey(this, "");
            etApiKey.setText("");
            tvHint.setText("密钥已删除");
            tvHint.setTextColor(Color.parseColor("#EF4444"));
            previewSection.setVisibility(View.GONE);
            Toast.makeText(this, "API Key 已删除", Toast.LENGTH_SHORT).show();
            updateAllWidgets();
        });
    }

    private void fetchBalancePreview(final String apiKey) {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("https://api.deepseek.com/user/balance");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    java.io.InputStream is = conn.getInputStream();
                    java.util.Scanner s = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
                    String json = s.hasNext() ? s.next() : "";
                    s.close();
                    conn.disconnect();

                    org.json.JSONObject data = new org.json.JSONObject(json);
                    boolean available = data.optBoolean("is_available", true);
                    org.json.JSONArray infos = data.optJSONArray("balance_infos");

                    if (infos != null && infos.length() > 0) {
                        org.json.JSONObject primary = infos.getJSONObject(0);
                        final String balance = formatBalance(primary.optString("total_balance", "0"));
                        final String currency = primary.optString("currency", "CNY");
                        final String status = available ? "✓ 账户可用" : "⚠ 余额不足";

                        runOnUiThread(() -> {
                            tvPreviewBalance.setText(balance);
                            tvPreviewStatus.setText(currency + "  " + status);
                            tvPreviewStatus.setTextColor(available ?
                                    Color.parseColor("#10B981") : Color.parseColor("#EF4444"));
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        tvPreviewBalance.setText("--");
                        tvPreviewStatus.setText("请求失败: HTTP " + code);
                        tvPreviewStatus.setTextColor(Color.parseColor("#EF4444"));
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvPreviewBalance.setText("--");
                    tvPreviewStatus.setText("请求失败: " + e.getMessage());
                    tvPreviewStatus.setTextColor(Color.parseColor("#EF4444"));
                });
            }
        }).start();
    }

    private String formatBalance(String val) {
        try {
            double d = Double.parseDouble(val);
            return String.format("%.2f", d);
        } catch (Exception e) {
            return val;
        }
    }

    private void updateAllWidgets() {
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        ComponentName name = new ComponentName(this, BalanceWidgetProvider.class);
        int[] ids = mgr.getAppWidgetIds(name);
        if (ids.length > 0) {
            BalanceWidgetProvider.updateWidgets(this, mgr, ids);
        }
    }

    // ── SharedPreferences 工具方法 ──

    public static String getApiKey(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_API_KEY, "");
    }

    public static void saveApiKey(Context ctx, String key) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_API_KEY, key).apply();
    }
}
