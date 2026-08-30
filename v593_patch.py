# -*- coding: utf-8 -*-
# پچ v5.9.3 — کرش ساید منو (دفاعی) + حذف دکمهٔ سرچ هدر + نسخه + UX دنبال‌شده‌ها + دیالوگ افزودن بعد اسکن
import io, re

# ═══ ۱) MainActivity: bindSideMenu ضدکرش + حذف سرچ هدر + دیالوگ بعد از اسکن ═══
p = 'java/ir/moeshakteam/moeshakmusic/ui/MainActivity.java'
s = io.open(p, encoding='utf-8').read()

s = s.replace('''    private void bindSideMenu() {
        // ساید منو فقط امکانات جانبی — همهٔ بخش‌ها تب هستند
        View.OnClickListener go = x -> {
            drawer.closeDrawer(Gravity.START);
            int id = x.getId();
            if (id == R.id.sideProxy) {
                showFullScreen(new ProxyFragment());
            } else if (id == R.id.sideLog) {
                showFullScreen(new LogFragment());
            } else if (id == R.id.sideSettings) {
                showFullScreen(new SettingsFragment());
            } else if (id == R.id.sideTheme) {
                int mode = (Prefs.get(this).themeMode() + 1) % 3;
                Prefs.get(this).setThemeMode(mode);
                App.applyTheme(this);
            }
        };
        int[] ids = {R.id.sideProxy, R.id.sideLog, R.id.sideSettings, R.id.sideTheme};
        for (int res : ids) {
            View vv = findViewById(res);
            if (vv != null) vv.setOnClickListener(go);
        }
    }''','''    private void bindSideMenu() {
        // ساید منو فقط امکانات جانبی — همهٔ بخش‌ها تب هستند
        // 💥 ضدکرش: هیچ findViewById نباید NPE بدهد حتی اگر XML تغییر کرد
        try {
            View.OnClickListener go = x -> {
                try {
                    drawer.closeDrawer(Gravity.START);
                    int id = x.getId();
                    if (id == R.id.sideProxy) {
                        showFullScreen(new ProxyFragment());
                    } else if (id == R.id.sideLog) {
                        showFullScreen(new LogFragment());
                    } else if (id == R.id.sideSettings) {
                        showFullScreen(new SettingsFragment());
                    } else if (id == R.id.sideTheme) {
                        int mode = (Prefs.get(this).themeMode() + 1) % 3;
                        Prefs.get(this).setThemeMode(mode);
                        App.applyTheme(this);
                    }
                } catch (Throwable t) {
                    ir.moeshakteam.moeshakmusic.data.Tg.log("⚠️ sideMenu tap: " + t);
                }
            };
            int[] ids = {R.id.sideProxy, R.id.sideLog, R.id.sideSettings, R.id.sideTheme};
            for (int res : ids) {
                View vv = findViewById(res);
                if (vv != null) vv.setOnClickListener(go);
            }
        } catch (Throwable t) {
            ir.moeshakteam.moeshakmusic.data.Tg.log("⚠️ bindSideMenu: " + t);
        }
    }''')

# حذف وایر کردن دکمهٔ سرچ هدر (SearchView خودش لمس‌پذیر است)
s = s.replace('''        findViewById(R.id.btnSearchIcon).setOnClickListener(x -> {
            searchBar.setIconified(false);
            searchBar.requestFocus();
        });
''', '')

io.open(p, 'w', encoding='utf-8').write(s)
print('1. MainActivity ✓')

# activity_main.xml — حذف btnSearchIcon از هدر
p = 'res/layout/activity_main.xml'
s = io.open(p, encoding='utf-8').read()
m = re.search(r'\s*<ImageButton\s+android:id="@\+id/btnSearchIcon".*?/>', s, re.S)
if m:
    s = s[:m.start()] + s[m.end():]
    io.open(p, 'w', encoding='utf-8').write(s)
    print('2. btnSearchIcon از هدر حذف ✓')
else:
    print('2. btnSearchIcon نبود')

# ═══ ۳) نسخه در تنظیمات ═══
p = 'res/values/strings.xml'
s = io.open(p, encoding='utf-8').read()
s = re.sub(r'<string name="version">[^<]*</string>',
           '<string name="version">نسخه ۵.۹.۳</string>', s)
io.open(p, 'w', encoding='utf-8').write(s)
p = 'res/values-en/strings.xml'
s = io.open(p, encoding='utf-8').read()
s = re.sub(r'<string name="version">[^<]*</string>',
           '<string name="version">Version 5.9.3</string>', s)
io.open(p, 'w', encoding='utf-8').write(s)
print('3. version ✓')

# ═══ ۴) ScanFragment — بعد از اتمام اسکن، دیالوگ «افزودن به کتابخانه؟» ═══
p = 'java/ir/moeshakteam/moeshakmusic/ui/ScanFragment.java'
s = io.open(p, encoding='utf-8').read()
s = s.replace('''            @Override
            public void onDone(int total) {
                main.post(() -> {
                    if (!isAdded()) return;
                    running = false;
                    btnScan.setText(R.string.scan_start);
                    state.setText(R.string.scan_ready);
                    long sec = (System.currentTimeMillis() - startedAt) / 1000;
                    detail.setText(getString(R.string.scan_done_line, sec));
                    bar.setIndeterminate(false);
                    refreshResults();
                    updateButtons();
                    Ui.toast(requireContext(), getString(R.string.scan_finished, Tg.get(requireContext()).scanResults.size()));
                });
            }''','''            @Override
            public void onDone(int total) {
                main.post(() -> {
                    if (!isAdded()) return;
                    running = false;
                    btnScan.setText(R.string.scan_start);
                    state.setText(R.string.scan_ready);
                    long sec = (System.currentTimeMillis() - startedAt) / 1000;
                    detail.setText(getString(R.string.scan_done_line, sec));
                    bar.setIndeterminate(false);
                    refreshResults();
                    updateButtons();
                    // 🎁 دیالوگ پیشنهادی — تا نتایج قاطی کتابخانه شوند بدون قدم اضافه
                    int n = Tg.get(requireContext()).scanResults.size();
                    if (n > 0 && isAdded()) {
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                .setTitle(getString(R.string.scan_finished, n))
                                .setMessage(R.string.scan_add_dialog)
                                .setPositiveButton(R.string.scan_add_yes, (d2, w2) -> addToLibrary())
                                .setNegativeButton(R.string.no, null)
                                .show();
                    }
                });
            }''')
io.open(p, 'w', encoding='utf-8').write(s)
print('4. ScanFragment dialog ✓')

# استرینگ‌های دیالوگ
p = 'res/values/strings.xml'
s = io.open(p, encoding='utf-8').read()
s = s.replace('</resources>', '''    <string name="scan_add_dialog">این آهنگ‌ها به کتابخانه اضافه شوند؟ (تا وقتی اپ نصب است می‌مانند)</string>
    <string name="scan_add_yes">➕ بله، اضافه کن</string>
</resources>''')
io.open(p, 'w', encoding='utf-8').write(s)
p = 'res/values-en/strings.xml'
s = io.open(p, encoding='utf-8').read()
s = s.replace('</resources>', '''    <string name="scan_add_dialog">Add these tracks to the library? (they stay until the app is uninstalled)</string>
    <string name="scan_add_yes">➕ Yes, add</string>
</resources>''')
io.open(p, 'w', encoding='utf-8').write(s)
print('5. strings dialog ✓')
