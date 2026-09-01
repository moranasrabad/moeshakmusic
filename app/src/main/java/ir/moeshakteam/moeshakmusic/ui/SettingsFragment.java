package ir.moeshakteam.moeshakmusic.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import ir.moeshakteam.moeshakmusic.App;
import ir.moeshakteam.moeshakmusic.R;
import ir.moeshakteam.moeshakmusic.data.Prefs;
import ir.moeshakteam.moeshakmusic.data.Tg;
import ir.moeshakteam.moeshakmusic.util.Ui;

/** تنظیمات + دربارهٔ تیم موشک */
public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        TextView tvName = v.findViewById(R.id.tvName);
        TextView tvPhone = v.findViewById(R.id.tvPhone);
        MaterialButton btnLogout = v.findViewById(R.id.btnLogout);
        MaterialButton btnSite = v.findViewById(R.id.btnSite);
        RadioGroup rgTheme = v.findViewById(R.id.rgTheme);

        v.findViewById(R.id.btnBack).setOnClickListener(x ->
                requireActivity().onBackPressed());

        // 📌 نسخه — از BuildConfig (خودکار)
        try {
            android.widget.TextView tvVer = v.findViewById(R.id.tvVersion);
            if (tvVer != null) {
                tvVer.setText("نسخه " + ir.moeshakteam.moeshakmusic.BuildConfig.VERSION_NAME
                        + " (" + ir.moeshakteam.moeshakmusic.BuildConfig.VERSION_CODE + ")");
            }
        } catch (Throwable ignored) {
        }

        v.findViewById(R.id.btnProxySettings).setOnClickListener(x ->
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fullScreenContainer, new ProxyFragment())
                        .addToBackStack("proxy")
                        .commit());

        btnSite.setOnClickListener(x ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://moeshakteam.ir"))));

        Tg.get(requireContext()).getAccount((name, phone, id) -> {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                tvName.setText(name);
                tvPhone.setText("+" + phone + " • id " + id);
            });
        });

        int mode = Prefs.get(requireContext()).themeMode();
        if (mode == 1) rgTheme.check(R.id.rbLight);
        else if (mode == 2) rgTheme.check(R.id.rbDark);
        else rgTheme.check(R.id.rbSystem);
        rgTheme.setOnCheckedChangeListener((g, checkedId) -> {
            int m = checkedId == R.id.rbLight ? 1 : checkedId == R.id.rbDark ? 2 : 0;
            Prefs.get(requireContext()).setThemeMode(m);
            App.applyTheme(requireContext());
        });

        // 🎨 انتخاب رنگ اکسنت — اعمال فوری با تم کامل (بدون نیاز به ری‌استارت)
        LinearLayout accentRow = v.findViewById(R.id.accentRow);
        int[] colors = {0, 0xFF22D3EE, 0xFF8B5CF6, 0xFF34D399, 0xFFF59E0B, 0xFFF43F5E, 0xFF3B82F6};
        int[] nameRes = {R.string.accent_default, R.string.accent_icy, R.string.accent_purple,
                R.string.accent_green, R.string.accent_amber, R.string.accent_rose, R.string.accent_blue};
        for (int i = 0; i < colors.length; i++) {
            final int idx = i;
            TextView dot = new TextView(requireContext());
            dot.setText(nameRes[i]);
            dot.setTextSize(12);
            int pad = (int) (10 * getResources().getDisplayMetrics().density);
            dot.setPadding(pad, pad / 2, pad, pad / 2);
            if (colors[i] != 0) {
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setColor(colors[i]);
                bg.setCornerRadius(20 * getResources().getDisplayMetrics().density);
                dot.setBackground(bg);
                dot.setTextColor(0xFF0B141C);
            } else {
                dot.setTextColor(getResources().getColor(R.color.moeshak_accent, requireActivity().getTheme()));
            }
            dot.setOnClickListener(x -> {
                Prefs.get(requireContext()).setAccentColor(colors[idx]);
                ir.moeshakteam.moeshakmusic.App.ACCENT = colors[idx];
                Ui.toast(requireContext(), R.string.accent_applied);
                requireActivity().recreate();
            });
            accentRow.addView(dot);
        }

        // 🌐 سوییچ زبان
        com.google.android.material.button.MaterialButton btnLang = new com.google.android.material.button.MaterialButton(requireContext());
        btnLang.setText("🌐 فارسی / English");
        btnLang.setOnClickListener(x -> {
            androidx.core.os.LocaleListCompat cur = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales();
            boolean isEn = !cur.isEmpty() && "en".equals(cur.toLanguageTags());
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                    androidx.core.os.LocaleListCompat.forLanguageTags(isEn ? "fa" : "en"));
        });
        ((LinearLayout) v.findViewById(R.id.accentRow).getParent()).addView(btnLang, 1);

        // 🔑 کلید API شخصی — وقتی با کلید پیش‌فرض کد ورود نمی‌آید
        try {
            com.google.android.material.textfield.TextInputEditText etId = v.findViewById(R.id.etApiId);
            com.google.android.material.textfield.TextInputEditText etHash = v.findViewById(R.id.etApiHash);
            if (etId != null && etHash != null) {
                Prefs p = Prefs.get(requireContext());
                if (p.apiId() != 0) etId.setText(String.valueOf(p.apiId()));
                if (p.apiHash().length() > 10) etHash.setText(p.apiHash());
                v.findViewById(R.id.btnSaveKeys).setOnClickListener(x -> {
                    try {
                        int id = Integer.parseInt(etId.getText() == null ? "" : etId.getText().toString().trim());
                        String hash = (etHash.getText() == null ? "" : etHash.getText().toString().trim());
                        if (id <= 0 || hash.length() < 32) {
                            Ui.toast(requireContext(), R.string.api_keys_invalid);
                            return;
                        }
                        p.saveKeys(id, hash);
                        Ui.toast(requireContext(), R.string.api_keys_saved);
                        v.postDelayed(this::restartApp, 900);
                    } catch (Throwable t) {
                        Ui.toast(requireContext(), R.string.api_keys_invalid);
                    }
                });
                v.findViewById(R.id.btnResetKeys).setOnClickListener(x -> {
                    p.clearKeys();
                    Ui.toast(requireContext(), R.string.api_keys_reset_done);
                    v.postDelayed(this::restartApp, 900);
                });
            }
        } catch (Throwable t) {
            Tg.log("⚠️ apiKeys UI: " + t);
        }

        // 🗑 پاک کردن کتابخانه — اسکن از اول
        try {
            View clearLib = v.findViewById(R.id.btnClearLibrary);
            if (clearLib != null) {
                clearLib.setOnClickListener(x -> new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.clear_library_title)
                        .setMessage(R.string.clear_library_confirm)
                        .setPositiveButton(R.string.yes, (d, w) -> {
                            Tg.get(requireContext()).clearLibrary();
                            Ui.toast(requireContext(), R.string.clear_library_done);
                        })
                        .setNegativeButton(R.string.no, null)
                        .show());
            }
        } catch (Throwable t) {
            Tg.log("⚠️ clearLib UI: " + t);
        }

        btnLogout.setOnClickListener(x -> new AlertDialog.Builder(requireContext())
                .setMessage(R.string.logout_confirm)
                .setPositiveButton(R.string.yes, (d, w) -> Tg.get(requireContext()).logout())
                .setNegativeButton(R.string.no, null)
                .show());
    }

    /** ری‌استارت کامل اپ — برای اعمال کلید API جدید */
    private void restartApp() {
        try {
            android.content.Intent i = requireActivity().getPackageManager()
                    .getLaunchIntentForPackage(requireContext().getPackageName());
            if (i != null) {
                i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                requireContext().startActivity(i);
            }
        } catch (Throwable ignored) {
        }
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
