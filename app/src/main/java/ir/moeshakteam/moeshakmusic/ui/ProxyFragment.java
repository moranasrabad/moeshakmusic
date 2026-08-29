package ir.moeshakteam.moeshakmusic.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.drinkless.tdlib.TdApi;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ir.moeshakteam.moeshakmusic.R;
import ir.moeshakteam.moeshakmusic.data.Prefs;
import ir.moeshakteam.moeshakmusic.data.Tg;
import ir.moeshakteam.moeshakmusic.td.TdClient;
import ir.moeshakteam.moeshakmusic.util.Ui;

/** مدیریت پروکسی‌ها: افزودن، پینگ، انتخاب — تیم موشک */
public class ProxyFragment extends Fragment {

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private ProxyAdapter adapter;
    private TextInputEditText etAdd;
    private TextView empty;
    private RecyclerView recycler;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_proxy, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        recycler = v.findViewById(R.id.recycler);
        empty = v.findViewById(R.id.empty);
        etAdd = v.findViewById(R.id.etAdd);
        adapter = new ProxyAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        refreshList();

        v.findViewById(R.id.btnBack).setOnClickListener(x ->
                requireActivity().onBackPressed());

        MaterialButton btnAdd = v.findViewById(R.id.btnAdd);
        MaterialButton btnPingAll = v.findViewById(R.id.btnPingAll);
        btnAdd.setOnClickListener(x -> addFromInput());
        btnPingAll.setOnClickListener(x -> pingAll());

        // ⚡ سوییچ خاموش/روشن پروکسی
        com.google.android.material.materialswitch.MaterialSwitch sw =
                v.findViewById(R.id.swProxy);
        Prefs p0 = Prefs.get(requireContext());
        if (sw == null) return;
        sw.setChecked(p0.proxyEnabled() && p0.activeProxyIndex() >= 0);
        sw.setOnCheckedChangeListener((g, on) -> {
            Prefs p = Prefs.get(requireContext());
            if (on) {
                if (p.proxies().isEmpty()) {
                    sw.setChecked(false);
                    Ui.toast(requireContext(), R.string.proxy_none_yet);
                    return;
                }
                int idx = p.lastProxyIndex();
                if (idx < 0 || idx >= p.proxies().size()) idx = 0;
                p.setActiveProxyIndex(idx);
                Tg.get(requireContext()).applyProxy();
                Ui.toast(requireContext(), R.string.proxy_active);
                refreshList();
            } else {
                p.setActiveProxyIndex(-1);
                Tg.get(requireContext()).applyProxy();
                Ui.toast(requireContext(), R.string.proxy_disabled);
                refreshList();
            }
        });
    }

    private void refreshList() {
        adapter.setItems(Prefs.get(requireContext()).proxies(),
                Prefs.get(requireContext()).activeProxyIndex());
        empty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    /** پارس لینک tg://proxy یا host:port:secret — با تحمل فرمت‌های مختلف */
    private void addFromInput() {
        String s = etAdd.getText() == null ? "" : etAdd.getText().toString().trim();
        if (s.isEmpty()) {
            Ui.toast(requireContext(), getString(R.string.proxy_bad));
            return;
        }
        // تمیزکاری: خط جدید و فاصله‌های اضافه
        s = s.replace("\n", " ").replaceAll("\\s+", " ").trim();
        java.util.regex.Matcher tg = java.util.regex.Pattern
                .compile("(?i)(?:tg://proxy\\?|https?://t\\.me/proxy\\?|https?://telegram\\.me/proxy\\?).*?(?:server=([^&\\s]+)).*?(?:port=(\\d+)).*?(?:secret=([a-zA-Z0-9]+))")
                .matcher(s);
        java.util.regex.Matcher raw = java.util.regex.Pattern
                .compile("(?i)(?:mtproxy://)?([a-zA-Z0-9\\.\\-]+):(\\d{2,5})[/:#\\s]?([a-zA-Z0-9]{16,})")
                .matcher(s);
        String server = null, secret = null;
        int port = 443;
        try {
            if (tg.find()) {
                server = tg.group(1);
                port = Integer.parseInt(tg.group(2));
                secret = tg.group(3);
            } else if (raw.find()) {
                server = raw.group(1);
                port = Integer.parseInt(raw.group(2));
                secret = raw.group(3);
            } else if (s.contains(":")) {
                String[] parts = s.split(":");
                if (parts.length >= 3) {
                    server = parts[parts.length - 3].trim();
                    port = Integer.parseInt(parts[parts.length - 2].trim());
                    secret = parts[parts.length - 1].trim();
                }
            }
        } catch (Exception ignored) {
        }
        if (server == null || server.isEmpty() || secret == null || secret.length() < 16) {
            Ui.toast(requireContext(), getString(R.string.proxy_bad));
            return;
        }
        Prefs p = Prefs.get(requireContext());
        List<Prefs.ProxyEntry> list = p.proxies();
        for (Prefs.ProxyEntry e : list) {
            if (e.server.equals(server) && e.port == port && e.secret.equals(secret)) {
                Ui.toast(requireContext(), getString(R.string.proxy_dup));
                return;
            }
        }
        Prefs.ProxyEntry e = new Prefs.ProxyEntry();
        e.server = server;
        e.port = port;
        e.secret = secret;
        e.comment = "";
        list.add(e);
        p.saveProxies(list);
        etAdd.setText("");
        refreshList();
        Ui.toast(requireContext(), getString(R.string.proxy_added));
    }

    private void pingAll() {
        List<Prefs.ProxyEntry> list = Prefs.get(requireContext()).proxies();
        if (list.isEmpty()) return;
        Ui.toast(requireContext(), getString(R.string.proxy_pinging));
        exec.execute(() -> {
            for (int i = 0; i < list.size(); i++) {
                final int idx = i;
                try {
                    Prefs.ProxyEntry e = list.get(i);
                    TdApi.Proxy pr = new TdApi.Proxy(e.server, e.port, new TdApi.ProxyTypeMtproto(e.secret));
                    TdApi.Seconds s = (TdApi.Seconds) TdClient.sync(new TdApi.PingProxy(pr));
                    e.pingMs = (int) Math.round(s.seconds * 1000);
                } catch (Exception ex) {
                    list.get(idx).pingMs = -2; // خطا
                }
                // 🔧 فیکس: نتیجه پینگ باید ذخیره بشه وگرنه رفرش، مقادیر قدیمی رو نشون می‌داد
                Prefs.get(requireContext()).saveProxies(list);
                main.post(this::refreshList);
            }
            main.post(() -> Ui.toast(requireContext(), getString(R.string.proxy_ping_done)));
        });
    }

    private void useProxy(int idx) {
        Prefs p = Prefs.get(requireContext());
        p.setActiveProxyIndex(idx);
        Tg.get(requireContext()).applyProxy();
        refreshList();
        if (idx >= 0) Ui.toast(requireContext(), getString(R.string.proxy_active));
        else Ui.toast(requireContext(), getString(R.string.proxy_disabled));
    }

    private void deleteProxy(int idx) {
        Prefs p = Prefs.get(requireContext());
        List<Prefs.ProxyEntry> list = p.proxies();
        if (idx < 0 || idx >= list.size()) return;
        list.remove(idx);
        p.saveProxies(list);
        if (p.activeProxyIndex() == idx) p.setActiveProxyIndex(-1);
        refreshList();
    }

    private void pingOne(int idx) {
        List<Prefs.ProxyEntry> list = Prefs.get(requireContext()).proxies();
        if (idx < 0 || idx >= list.size()) return;
        list.get(idx).pingMs = -3; // در حال تست
        Prefs.get(requireContext()).saveProxies(list);
        refreshList();
        exec.execute(() -> {
            try {
                Prefs.ProxyEntry e = list.get(idx);
                TdApi.Proxy pr = new TdApi.Proxy(e.server, e.port, new TdApi.ProxyTypeMtproto(e.secret));
                TdApi.Seconds s = (TdApi.Seconds) TdClient.sync(new TdApi.PingProxy(pr));
                e.pingMs = (int) Math.round(s.seconds * 1000);
            } catch (Exception ex) {
                list.get(idx).pingMs = -2;
            }
            Prefs.get(requireContext()).saveProxies(list);
            main.post(this::refreshList);
        });
    }

    // ---------- آداپتور ----------

    private class ProxyAdapter extends RecyclerView.Adapter<VH> {
        private List<Prefs.ProxyEntry> items;
        private int active = -1;

        void setItems(List<Prefs.ProxyEntry> list, int activeIdx) {
            items = list;
            active = activeIdx;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_proxy, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            int idx = h.getBindingAdapterPosition();
            Prefs.ProxyEntry e = items.get(idx);
            h.tvServer.setText(e.label());
            String ping;
            if (e.pingMs == -3) ping = getString(R.string.proxy_ping_unknown);
            else if (e.pingMs == -2) ping = getString(R.string.proxy_ping_fail);
            else if (e.pingMs < 0) ping = getString(R.string.proxy_ping_unknown);
            else ping = getString(R.string.proxy_ping_ms, e.pingMs);
            h.tvPing.setText(ping);
            h.rbActive.setChecked(idx == active);
            h.rbActive.setOnClickListener(x -> useProxy(idx));
            h.itemView.setOnClickListener(x -> useProxy(idx));
            h.btnPing.setOnClickListener(x -> pingOne(idx));
            h.btnDel.setOnClickListener(x -> deleteProxy(idx));
        }

        @Override
        public int getItemCount() {
            return items == null ? 0 : items.size();
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        final RadioButton rbActive;
        final TextView tvServer, tvPing;
        final ImageButton btnPing, btnDel;

        VH(@NonNull View v) {
            super(v);
            rbActive = v.findViewById(R.id.rbActive);
            tvServer = v.findViewById(R.id.tvServer);
            tvPing = v.findViewById(R.id.tvPing);
            btnPing = v.findViewById(R.id.btnPing);
            btnDel = v.findViewById(R.id.btnDel);
        }
    }
}
