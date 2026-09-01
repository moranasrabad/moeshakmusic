package ir.moeshakteam.moeshakmusic.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ir.moeshakteam.moeshakmusic.R;
import ir.moeshakteam.moeshakmusic.data.Track;
import ir.moeshakteam.moeshakmusic.data.Tg;
import ir.moeshakteam.moeshakmusic.util.Ui;

/**
 * مرورگر اکانت: چت‌ها بر اساس پوشه‌ها + اسکن عمیق دستی هر چت — تیم موشک
 */
public class ChatsFragment extends Fragment {

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private LinearLayout tabs;
    private RecyclerView recycler;
    private ProgressBar progress;
    private TextView empty, tvAccount;
    private android.widget.EditText etSearch;
    private ChatAdapter adapter;
    private int selectedTab; // 0=اصلی 1=آرشیو 2+ = پوشه‌ها
    private long myId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        tabs = v.findViewById(R.id.tabs);
        recycler = v.findViewById(R.id.recycler);
        progress = v.findViewById(R.id.progress);
        empty = v.findViewById(R.id.empty);
        tvAccount = v.findViewById(R.id.tvAccount);
        etSearch = v.findViewById(R.id.etChatSearch);
        if (etSearch != null) etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                adapter.filter(s == null ? "" : s.toString());
            }
        });

        adapter = new ChatAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        Tg.get(requireContext()).getAccount((name, phone, id) -> main.post(() -> {
            if (isAdded()) {
                tvAccount.setText("👤 " + name + " • +" + phone);
                try {
                    myId = Long.parseLong(id);
                } catch (Exception ignored) {
                }
            }
        }));

        buildTabs();
        selectTab(0);
        // اگر پوشه‌ها هنوز از سرور نرسیدن، بعد از ۳ ثانیه تب‌ها رو دوباره بساز
        main.postDelayed(() -> {
            if (!isAdded()) return;
            if (Tg.get(requireContext()).folders.size() > tabs.getChildCount() - 2) {
                buildTabs();
            }
        }, 3000);
    }

    private void buildTabs() {
        tabs.removeAllViews();
        addTab(0, getString(R.string.folder_main));
        addTab(1, getString(R.string.folder_archive));
        List<TdApi.ChatFolderInfo> folders = Tg.get(requireContext()).folders;
        for (int i = 0; i < folders.size(); i++) {
            String name = folderName(folders.get(i));
            addTab(i + 2, name);
        }
    }

    private String folderName(TdApi.ChatFolderInfo fi) {
        try {
            // ChatFolderName.text = FormattedText → .text = String
            if (fi.name != null && fi.name.text != null && fi.name.text.text != null && !fi.name.text.text.isEmpty()) {
                return fi.name.text.text;
            }
        } catch (Exception ignored) {
        }
        return "پوشه";
    }

    private void addTab(int index, String label) {
        MaterialButton b = new MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(label);
        b.setTextSize(12);
        b.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(4, 4, 4, 4);
        b.setLayoutParams(lp);
        b.setOnClickListener(x -> selectTab(index));
        b.setTag(index);
        tabs.addView(b);
        styleTab(index);
    }

    private void styleTab(int index) {
        for (int i = 0; i < tabs.getChildCount(); i++) {
            View c = tabs.getChildAt(i);
            if (c instanceof MaterialButton) {
                ((MaterialButton) c).setSelected((int) c.getTag() == selectedTab);
            }
        }
    }

    private void selectTab(int index) {
        selectedTab = index;
        styleTab(index);
        loadList();
    }

    private TdApi.ChatList listFor(int index) {
        if (index == 0) return new TdApi.ChatListMain();
        if (index == 1) return new TdApi.ChatListArchive();
        List<TdApi.ChatFolderInfo> folders = Tg.get(requireContext()).folders;
        int fi = index - 2;
        if (fi >= 0 && fi < folders.size()) return new TdApi.ChatListFolder(folders.get(fi).id);
        return new TdApi.ChatListMain();
    }

    private void loadList() {
        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        adapter.setItems(new ArrayList<>());
        final TdApi.ChatList cl = listFor(selectedTab);
        exec.execute(() -> {
            List<TdApi.Chat> chats = new ArrayList<>();
            try {
                chats = Tg.get(requireContext()).loadChatsOfList(cl, 500);
            } catch (Exception ignored) {
            }
            final List<TdApi.Chat> fc = chats;
            main.post(() -> {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                adapter.setItems(fc);
                empty.setVisibility(fc.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private String chatTypeLabel(TdApi.Chat c) {
        if (c.type instanceof TdApi.ChatTypePrivate) {
            return c.id == myId ? "پیام‌های ذخیره‌شده ⭐" : "چت خصوصی";
        }
        if (c.type instanceof TdApi.ChatTypeSupergroup) {
            return ((TdApi.ChatTypeSupergroup) c.type).isChannel ? "کانال 📢" : "سوپرگروه";
        }
        if (c.type instanceof TdApi.ChatTypeBasicGroup) return "گروه";
        if (c.type instanceof TdApi.ChatTypeSecret) return "چت مخفی";
        return "چت";
    }

    private int countFor(long chatId) {
        int n = 0;
        for (Track t : Tg.get(requireContext()).library) {
            if (t.chatId == chatId) n++;
        }
        return n;
    }

    // ---------- آداپتور ----------

    private class ChatAdapter extends RecyclerView.Adapter<VH> {
        private List<TdApi.Chat> all = new ArrayList<>();
        private List<TdApi.Chat> items = new ArrayList<>();
        private String query = "";

        void setItems(List<TdApi.Chat> list) {
            all = list;
            refilter();
        }

        void filter(String q) {
            query = q == null ? "" : q;
            refilter();
        }

        private void refilter() {
            items.clear();
            String n = query.toLowerCase().trim();
            for (TdApi.Chat c : all) {
                if (n.isEmpty() || (c.title != null && c.title.toLowerCase().contains(n))) items.add(c);
            }
            notifyDataSetChanged();
            empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            final int idx = h.getBindingAdapterPosition();
            TdApi.Chat c = items.get(idx);
            h.tvTitle.setText(c.title == null ? "—" : c.title);
            h.tvType.setText(chatTypeLabel(c));
            int n = countFor(c.id);
            h.tvCount.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
            h.tvCount.setText(String.valueOf(n));
            h.itemView.setOnClickListener(x -> showChatTracks(c));
            h.itemView.setOnLongClickListener(x -> {
                showChatMenu(c);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvTitle, tvType, tvCount;
        final android.widget.ImageView ivType;

        VH(@NonNull View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvType = v.findViewById(R.id.tvType);
            tvCount = v.findViewById(R.id.tvCount);
            ivType = v.findViewById(R.id.ivType);
        }
    }

    /** منوی لمس طولانی چت: دنبال کردن / اسکن عمیق — تیم موشک */
    private void showChatMenu(TdApi.Chat c) {
        ir.moeshakteam.moeshakmusic.data.FollowStore fs =
                ir.moeshakteam.moeshakmusic.data.FollowStore.get(requireContext());
        boolean fol = fs.isFollowed(c.id);
        String[] opts = {
                fol ? getString(R.string.followed_unfollow) : getString(R.string.followed_follow),
                getString(R.string.chat_deep_scan)
        };
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(c.title == null ? "چت" : c.title)
                .setItems(opts, (d, w) -> {
                    if (w == 0) {
                        if (fol) {
                            fs.unfollow(c.id);
                            Ui.toast(requireContext(), R.string.followed_unfollowed);
                        } else {
                            Tg.get(requireContext()).followAndDeepScan(c.id,
                                    c.title == null ? "چت" : c.title);
                            Ui.toast(requireContext(), R.string.followed_deep_started);
                        }
                    } else {
                        deepScan(c);
                    }
                }).show();
    }

    private void deepScan(TdApi.Chat c) {
        progress.setVisibility(View.VISIBLE);
        Tg.log("🔎 اسکن عمیق «" + (c.title == null ? "" : c.title) + "» درخواست شد");
        Ui.toast(requireContext(), getString(R.string.deep_scan_started, c.title == null ? "" : c.title));
        Tg.get(requireContext()).deepScanChat(c.id, new Tg.ScanListener() {
            @Override
            public void onProgress(int found, int chats) {
            }

            @Override
            public void onDone(int added) {
                main.post(() -> {
                    if (!isAdded()) return;
                    progress.setVisibility(View.GONE);
                    if (added > 0) {
                        Ui.toast(requireContext(), getString(R.string.deep_scan_done, added));
                        adapter.notifyDataSetChanged();
                        showChatTracks(c);
                    } else {
                        Ui.toast(requireContext(), getString(R.string.deep_scan_none));
                    }
                });
            }

            @Override
            public void onError(String msg) {
                main.post(() -> {
                    if (!isAdded()) return;
                    progress.setVisibility(View.GONE);
                    Ui.toast(requireContext(), msg);
                });
            }
        });
    }

    /** لیست موزیک‌های این چت + دکمه اسکن عمیق */
    private void showChatTracks(TdApi.Chat c) {
        String title = c.title == null || c.title.isEmpty() ? getString(R.string.tab_chats) : c.title;
        if (requireActivity() instanceof MainActivity) {
            ChannelTracksFragment f = new ChannelTracksFragment();
            Bundle b = new Bundle();
            b.putLong(ChannelTracksFragment.ARG_CHAT_ID, c.id);
            b.putString(ChannelTracksFragment.ARG_CHAT_TITLE, title);
            f.setArguments(b);
            ((MainActivity) requireActivity()).showFullScreen(f);
        }
    }
}
