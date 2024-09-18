package xtr.keymapper.editor;

import android.app.Activity;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import xtr.keymapper.IRemoteService;
import xtr.keymapper.R;
import xtr.keymapper.keymap.KeymapConfig;
import xtr.keymapper.server.RemoteServiceHelper;

public class EditorActivity extends Activity implements EditorUI.OnHideListener {
    public static final String PROFILE_NAME = "profile";
    private EditorUI editor;
    private IRemoteService mService;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String selectedProfile = getIntent().getStringExtra(PROFILE_NAME);
        if (selectedProfile == null) {
            finish();
            return;
        }

        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
        RemoteServiceHelper.getInstance(this, service -> mService = service);

        if (editor != null) editor.hideView();

        setTheme(R.style.Theme_XtMapper);
        editor = new EditorUI(this, this, selectedProfile);
        KeymapConfig keymapConfig = new KeymapConfig(this);
        editor.open(keymapConfig.editorOverlay);

        if (getEvent())
            // Can receive key events from remote service
            try {
                mService.registerOnKeyEventListener(editor);
                mService.pauseMouse();
            } catch (RemoteException e) {
                Log.e("editorActivity", e.getMessage(), e);
            }
        else {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);

            builder.setMessage(R.string.dialog_alert_editor)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {})
                    .setTitle(R.string.dialog_alert_editor_title);
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (getEvent()) try {
            mService.unregisterOnKeyEventListener(editor);
            mService.resumeMouse();
            mService.reloadKeymap();
        } catch (RemoteException ignored) {
        }
        editor = null;
    }

    @Override
    public void onHideView() {
        finish();
    }

    @Override
    public boolean getEvent() {
        return mService != null;
    }
}