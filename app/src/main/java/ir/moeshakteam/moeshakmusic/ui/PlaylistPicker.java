package ir.moeshakteam.moeshakmusic.ui;

import android.app.Activity;
import android.widget.EditText;

import java.util.List;

import ir.moeshakteam.moeshakmusic.R;
import ir.moeshakteam.moeshakmusic.data.PlaylistStore;
import ir.moeshakteam.moeshakmusic.data.Track;
import ir.moeshakteam.moeshakmusic.util.Ui;

/** انتخاب/ساخت پلی‌لیست برای گروهی از تراک‌ها — تیم موشک */
public final class PlaylistPicker {

    public interface Done {
        void done(String name, int added);
    }

    public static void show(Activity act, List<Track> tracks) {
        show(act, tracks, null);
    }

    public static void show(Activity act, List<Track> tracks, Done done) {
        PlaylistStore ps = PlaylistStore.get(act);
        List<PlaylistStore.Playlist> all = ps.all();
        if (all.isEmpty()) {
            promptNew(act, tracks, ps, done);
            return;
        }
        String[] names = new String[all.size()];
        for (int i = 0; i < all.size(); i++) names[i] = all.get(i).name + " (" + all.get(i).tracks.size() + ")";
        new androidx.appcompat.app.AlertDialog.Builder(act)
                .setTitle(act.getString(R.string.add_n_to_playlist, tracks.size()))
                .setItems(names, (d, w) -> {
                    String name = all.get(w).name;
                    int added = 0;
                    for (Track t : tracks) if (ps.addTrack(name, t)) added++;
                    Ui.toast(act, added + " موزیک به «" + name + "» اضافه شد ✓");
                    if (done != null) done.done(name, added);
                })
                .setPositiveButton("پلی‌لیست جدید…", (d, w) -> promptNew(act, tracks, ps, done))
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private static void promptNew(Activity act, List<Track> tracks, PlaylistStore ps, Done done) {
        EditText et = new EditText(act);
        et.setHint("نام پلی‌لیست");
        et.setPadding(48, 24, 48, 24);
        new androidx.appcompat.app.AlertDialog.Builder(act)
                .setTitle("پلی‌لیست جدید")
                .setView(et)
                .setPositiveButton("ساخت و افزودن", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) return;
                    ps.create(name);
                    int added = 0;
                    for (Track t : tracks) if (ps.addTrack(name, t)) added++;
                    Ui.toast(act, added + " موزیک به «" + name + "» اضافه شد ✓");
                    if (done != null) done.done(name, added);
                })
                .setNegativeButton(R.string.no, null).show();
    }
}
