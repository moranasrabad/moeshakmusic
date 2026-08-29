package ir.moeshakteam.moeshakmusic.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import ir.moeshakteam.moeshakmusic.R;
import ir.moeshakteam.moeshakmusic.data.Tg;

/** لاگ زندهٔ همه‌چیز: اتصال، ورود، اسکن تک‌تک چت‌ها، پیدا شدن موزیک‌ها — تیم موشک */
public class LogFragment extends Fragment {

    private TextView tvLog;
    private ScrollView scroll;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int lastHash;

    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            if (!isAdded()) return;
            refresh();
            main.postDelayed(this, 1000);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_log, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        tvLog = v.findViewById(R.id.tvLog);
        scroll = v.findViewById(R.id.scroll);
        v.findViewById(R.id.btnBack).setOnClickListener(x ->
                requireActivity().onBackPressed());
        v.findViewById(R.id.btnShare).setOnClickListener(x -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, Tg.dumpLog(requireContext()));
            startActivity(Intent.createChooser(i, getString(R.string.log_share)));
        });
        refresh();
    }

    private void refresh() {
        String text = Tg.dumpLog(requireContext());
        if (text.hashCode() == lastHash) return;
        lastHash = text.hashCode();
        boolean stick = !scroll.canScrollVertically(1);
        tvLog.setText(text);
        if (stick) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    public void onResume() {
        super.onResume();
        main.postDelayed(refresher, 500);
    }

    @Override
    public void onPause() {
        main.removeCallbacks(refresher);
        super.onPause();
    }
}
