package ir.moeshakteam.moeshakmusic.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import ir.moeshakteam.moeshakmusic.R;
import ir.moeshakteam.moeshakmusic.data.Prefs;
import ir.moeshakteam.moeshakmusic.data.Tg;

/** گرفتن API ID / API Hash در اولین اجرا — تیم موشک */
public class OnboardingFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        TextInputEditText etId = v.findViewById(R.id.etApiId);
        TextInputEditText etHash = v.findViewById(R.id.etApiHash);
        MaterialButton btnSave = v.findViewById(R.id.btnSave);
        MaterialButton btnGet = v.findViewById(R.id.btnGetApi);
        TextView tvError = v.findViewById(R.id.tvError);

        btnGet.setOnClickListener(x -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://my.telegram.org/apps"))));

        btnSave.setOnClickListener(x -> {
            String idStr = etId.getText() == null ? "" : etId.getText().toString().trim();
            String hash = etHash.getText() == null ? "" : etHash.getText().toString().trim();
            int apiId = 0;
            try {
                apiId = Integer.parseInt(idStr);
            } catch (Exception ignored) {
            }
            if (apiId <= 0 || hash.length() < 10) {
                tvError.setVisibility(View.VISIBLE);
                tvError.setText(R.string.enter_api_id);
                return;
            }
            Prefs.get(requireContext()).saveKeys(apiId, hash);
            Tg.get(requireContext()).start();
        });
    }
}
