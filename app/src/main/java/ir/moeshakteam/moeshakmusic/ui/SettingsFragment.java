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

        // 🎨 انتخاب رنگ اکسنت
        LinearLayout accentRow = v.findViewById(R.id.accentRow);
        int[] colors = {0, 0xFF22D3EE, 0xFF8B5CF6, 0xFF34D399, 0xFFF59E0B, 0xFFF43F5E, 0xFF3B82F6};
        String[] names = {"پیش‌فرض", "یخی", "بنفش", "سبز", "کهربایی", "سرخ", "آبی"};
        for (int i = 0; i < colors.length; i++) {
            final int idx = i;
            TextView dot = new TextView(requireContext());
            dot.setText(names[i]);
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
                Ui.toast(requireContext(), "رنگ اکسنت: " + names[idx] + " — اپ را ببند و باز کن");
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

        btnLogout.setOnClickListener(x -> new AlertDialog.Builder(requireContext())
                .setMessage(R.string.logout_confirm)
                .setPositiveButton(R.string.yes, (d, w) -> Tg.get(requireContext()).logout())
                .setNegativeButton(R.string.no, null)
                .show());
    }
}
