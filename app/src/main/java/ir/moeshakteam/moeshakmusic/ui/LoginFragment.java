package ir.moeshakteam.moeshakmusic.ui;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import ir.moeshakteam.moeshakmusic.R;
import ir.moeshakteam.moeshakmusic.data.Prefs;
import ir.moeshakteam.moeshakmusic.data.Tg;
import ir.moeshakteam.moeshakmusic.util.Ui;

/** ورود به اکانت تلگرام: شماره → کد → رمز + پشتیبانی پروکسی — تیم موشک */
public class LoginFragment extends Fragment implements Tg.AuthListener {

    private LinearLayout stepPhone, stepCode, stepPassword, stepQr;
    private TextView tvError, tvCodeHint, tvPwHint, tvConn;
    private TextInputEditText etPhone, etCode, etPassword;
    private android.widget.ImageView ivQr;
    private String lastQrLink;
    private boolean added;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        stepPhone = v.findViewById(R.id.stepPhone);
        stepCode = v.findViewById(R.id.stepCode);
        stepPassword = v.findViewById(R.id.stepPassword);
        stepQr = v.findViewById(R.id.stepQr);
        ivQr = v.findViewById(R.id.ivQr);
        tvError = v.findViewById(R.id.tvError);
        tvCodeHint = v.findViewById(R.id.tvCodeHint);
        tvPwHint = v.findViewById(R.id.tvPwHint);
        tvConn = v.findViewById(R.id.tvConn);
        etPhone = v.findViewById(R.id.etPhone);
        etCode = v.findViewById(R.id.etCode);
        etPassword = v.findViewById(R.id.etPassword);

        MaterialButton btnSend = v.findViewById(R.id.btnSendCode);
        MaterialButton btnVerify = v.findViewById(R.id.btnVerify);
        MaterialButton btnLogin = v.findViewById(R.id.btnLogin);
        TextView btnProxy = v.findViewById(R.id.btnProxy);

        btnSend.setOnClickListener(x -> {
            String phone = etPhone.getText() == null ? "" : etPhone.getText().toString().trim();
            // نرمال‌سازی: 0912... → +98912...  و 0098912... → +98912...
            String digits = phone.replaceAll("[^0-9+]", "");
            if (digits.startsWith("00")) digits = "+" + digits.substring(2);
            else if (digits.startsWith("0")) digits = "+98" + digits.substring(1);
            else if (!digits.startsWith("+")) digits = "+" + digits;
            phone = digits;
            if (phone.length() < 8) {
                showErr(getString(R.string.err_phone));
                return;
            }
            btnSend.setEnabled(false);
            main.postDelayed(() -> {
                if (isAdded()) btnSend.setEnabled(true);
            }, 4000);
            phoneSentAt = System.currentTimeMillis();
            Tg.get(requireContext()).sendPhone(phone);
        });
        btnForceCode = v.findViewById(R.id.btnForceCode);
        btnForceCode.setOnClickListener(x -> {
            Tg.get(requireContext()).forceWaitCode();
            updateUi();
        });
        btnVerify.setOnClickListener(x -> {
            String code = etCode.getText() == null ? "" : etCode.getText().toString().trim();
            if (code.isEmpty()) return;
            Tg.get(requireContext()).sendCode(code);
        });
        v.findViewById(R.id.btnResend).setOnClickListener(x ->
                Tg.get(requireContext()).resendCode());
        btnLogin.setOnClickListener(x -> {
            String pw = etPassword.getText() == null ? "" : etPassword.getText().toString();
            if (pw.isEmpty()) return;
            Tg.get(requireContext()).sendPassword(pw);
        });

        btnProxy.setOnClickListener(x ->
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fullScreenContainer, new ProxyFragment())
                        .addToBackStack("proxy")
                        .commit());

        v.findViewById(R.id.btnOpenLog).setOnClickListener(x ->
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fullScreenContainer, new LogFragment())
                        .addToBackStack("log")
                        .commit());
        v.findViewById(R.id.btnQrTop).setOnClickListener(x -> Tg.get(requireContext()).requestQr());
        v.findViewById(R.id.btnQrFromCode).setOnClickListener(x -> Tg.get(requireContext()).requestQr());
        v.findViewById(R.id.btnQrBack).setOnClickListener(x ->
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fullScreenContainer, new LoginFragment()).commitAllowingStateLoss());

        added = true;
        Tg.get(requireContext()).addAuthListener(this);
        updateUi();
        main.postDelayed(watchdog, 3000);

    }

    @Override
    public void onAuth(Tg.Auth a, String error) {
        if (!added || !isAdded()) return;
        requireActivity().runOnUiThread(this::updateUi);
    }

    /** پایش هوشمند: اگه کد رفته ولی صفحه کد باز نشده → دکمه اضطراری نشون بده */
    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (!added || !isAdded()) return;
            Tg tg = Tg.get(requireContext());
            if (tg.auth() == Tg.Auth.WAIT_PHONE && phoneSentAt > 0
                    && System.currentTimeMillis() - phoneSentAt > 15000) {
                btnForceCode.setVisibility(View.VISIBLE);
            }
            main.postDelayed(this, 3000);
        }
    };
    private long phoneSentAt;
    private View btnForceCode;

    @Override
    public void onConnState(String s) {
        if (!added || !isAdded()) return;
        requireActivity().runOnUiThread(() -> showConn(s));
    }

    private void showConn(String s) {
        if (s == null || s.isEmpty()) {
            tvConn.setVisibility(View.GONE);
            return;
        }
        tvConn.setVisibility(View.VISIBLE);
        switch (s) {
            case "ready":
                tvConn.setText(R.string.conn_ready);
                break;
            case "waiting":
                tvConn.setText(R.string.conn_waiting);
                break;
            case "updating":
                tvConn.setText(R.string.conn_updating);
                break;
            default:
                tvConn.setText(R.string.conn_connecting);
                break;
        }
    }

    private void showErr(String msg) {
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(msg);
    }

    private void updateUi() {
        try {
            doUpdateUi();
        } catch (Throwable t) {
            android.util.Log.e("LoginFrag", "updateUi crash", t);
            ir.moeshakteam.moeshakmusic.data.Tg.log("⚠️ خطای UI ورود: " + t);
        }
    }

    private void doUpdateUi() {
        Tg tg = Tg.get(requireContext());
        Tg.Auth a = tg.auth();
        String err = tg.authError();
        stepPhone.setVisibility(View.GONE);
        stepCode.setVisibility(View.GONE);
        stepPassword.setVisibility(View.GONE);
        stepQr.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);
        showConn(tg.connState);
        switch (a) {
            case LOADING:
                showErr(getString(R.string.logging_in));
                break;
            case WAIT_PHONE:
                stepPhone.setVisibility(View.VISIBLE);
                if (err != null && !err.isEmpty()) showErr(err);
                break;
            case WAIT_QR:
                stepQr.setVisibility(View.VISIBLE);
                String link = tg.qrLink;
                if (link.isEmpty()) {
                    tvConn.setVisibility(View.VISIBLE);
                    tvConn.setText(R.string.qr_building);
                } else if (!link.equals(lastQrLink)) {
                    lastQrLink = link;
                    android.graphics.Bitmap bmp = makeQr(link);
                    if (bmp != null) ivQr.setImageBitmap(bmp);
                    logQr(link);
                }
                if (err != null && !err.isEmpty()) showErr(err);
                break;
            case WAIT_CODE:
                stepCode.setVisibility(View.VISIBLE);
                tvCodeHint.setText(getString(R.string.code_hint_inapp));
                if (err != null && !err.isEmpty()) showErr(err);
                break;
            case WAIT_PASSWORD:
                stepPassword.setVisibility(View.VISIBLE);
                tvPwHint.setText(tg.passwordHint.isEmpty()
                        ? getString(R.string.password_hint)
                        : getString(R.string.password_hint) + " — " + tg.passwordHint);
                if (err != null && !err.isEmpty()) showErr(err);
                break;
            case ERROR:
                stepPhone.setVisibility(View.VISIBLE);
                showErr(err == null ? getString(R.string.err_network) : err);
                if (err != null && (err.contains("API") || err.contains("api"))) {
                    tvError.setOnClickListener(x -> {
                        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setMessage("API Keys رو از اول وارد می‌کنی؟")
                                .setPositiveButton(R.string.yes, (d, w) -> {
                                    Prefs.get(requireContext()).clear();
                                    requireActivity().recreate();
                                })
                                .setNegativeButton(R.string.no, null)
                                .show();
                    });
                }
                break;
            case CLOSED:
                stepPhone.setVisibility(View.VISIBLE);
                showErr(getString(R.string.err_network));
                break;
            default:
                break;
        }
    }

    private void logQr(String link) {
        ir.moeshakteam.moeshakmusic.data.Tg.log("🔳 QR جدید ساخته شد (لینک: " + link + ") — با تلگرام رسمی اسکنش کن");
    }

    /** تولید تصویر QR از لینک tg://login با zxing */
    private android.graphics.Bitmap makeQr(String text) {
        try {
            com.google.zxing.qrcode.QRCodeWriter w = new com.google.zxing.qrcode.QRCodeWriter();
            com.google.zxing.common.BitMatrix m = w.encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 720, 720);
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(720, 720, android.graphics.Bitmap.Config.RGB_565);
            for (int y = 0; y < 720; y++) {
                for (int x = 0; x < 720; x++) {
                    bmp.setPixel(x, y, m.get(x, y) ? 0xFF070A0D : 0xFFFFFFFF);
                }
            }
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onDestroyView() {
        added = false;
        main.removeCallbacksAndMessages(null);
        Tg.get(requireContext()).removeAuthListener(this);
        super.onDestroyView();
    }
}
