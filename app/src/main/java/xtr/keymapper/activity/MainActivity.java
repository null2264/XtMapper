package xtr.keymapper.activity;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.topjohnwu.superuser.Shell;

import xtr.keymapper.R;
import xtr.keymapper.Server;
import xtr.keymapper.TouchPointer;
import xtr.keymapper.databinding.ActivityMainBinding;
import xtr.keymapper.editor.EditorActivity;
import xtr.keymapper.fragment.SettingsFragment;
import xtr.keymapper.keymap.KeymapConfig;
import xtr.keymapper.server.RemoteServiceHelper;

public class MainActivity extends AppCompatActivity {
    public TouchPointer pointerOverlay;

    public ActivityMainBinding binding;
    private Intent intent;
    private ColorStateList defaultTint;
    private boolean stopped = true;

    static {
        // Set settings before the main shell can be created
        Shell.enableVerboseLogging = false;
        Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Context context = getApplicationContext();
        intent = new Intent(context, TouchPointer.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

        RemoteServiceHelper.useShizuku = new KeymapConfig(context).useShizuku;

        Shell.getShell(shell -> {
            Boolean rootAccess = Shell.isAppGrantedRoot();
            if (rootAccess == null || !rootAccess) {
                Server.setupServer(this, mCallback);
                if(!RemoteServiceHelper.useShizuku) alertRootAccessNotFound();
            }
            setupButtons();
        });
    }

    private void setupButtons() {
        defaultTint = binding.controls.launchApp.getBackgroundTintList();
        binding.controls.launchApp.setOnClickListener(v -> launchApp());
        binding.controls.startPointer.setOnClickListener(v -> startPointer());
        binding.controls.startEditor.setOnClickListener(v -> startEditor());
        binding.controls.configButton.setOnClickListener
                (v -> new SettingsFragment(this).show(getSupportFragmentManager(), "dialog"));
        binding.controls.aboutButton.setOnClickListener
                (v -> startActivity(new Intent(this, InfoActivity.class)));
    }

    private void launchApp() {
        if (!stopped) pointerOverlay.launchApp();
        else startPointer();
    }

    public void startPointer(){
        stopped = false;
        checkOverlayPermission();
        if(Settings.canDrawOverlays(this)) {
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
            startForegroundService(intent);
            setButtonState(false);
            requestNotificationPermission();
        }
        if (!RemoteServiceHelper.isRootService)
            alertRootAccessAndExit();
    }

    private void setButtonState(boolean start) {
        Button button = binding.controls.startPointer;
        if (start) {
            button.setText(R.string.start);
            button.setOnClickListener(v -> startPointer());
            button.setBackgroundTintList(defaultTint);
        } else {
            button.setText(R.string.stop);
            button.setOnClickListener(v -> stopPointer());
            button.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.purple_700)));
        }
    }

    public void stopPointer(){
        unbindTouchPointer();
        stopService(intent);
        setButtonState(true);
        stopped = true;
    }

    private void unbindTouchPointer() {
        if (pointerOverlay != null) {
            pointerOverlay.activityCallback = null;
            pointerOverlay = null;
        }
        unbindService(connection);
    }

    private void startEditor(){
        checkOverlayPermission();
        if(Settings.canDrawOverlays(this))
            startActivity(new Intent(this, EditorActivity.class));
    }

    private void checkOverlayPermission(){
        if (!Settings.canDrawOverlays(this)) {
            // send user to the device settings
            Intent myIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            startActivity(myIntent);
        }
    }

    private void requestNotificationPermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!(checkSelfPermission(POST_NOTIFICATIONS) ==
                    PERMISSION_GRANTED)) requestPermissions(new String[]{POST_NOTIFICATIONS}, 0);
        }
    }

    public void alertRootAccessNotFound() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.root_not_found_title)
                .setMessage(R.string.root_not_found_message)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    Intent launchIntent = MainActivity.this.getPackageManager().getLaunchIntentForPackage("me.weishu.kernelsu");
                    if (launchIntent != null) startActivity(launchIntent);
                });
        runOnUiThread(() -> builder.create().show());
    }

    public void alertRootAccessAndExit() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(MainActivity.this);
        builder.setTitle(R.string.root_no_privileges_title)
                .setMessage(R.string.root_no_privileges_message)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    finishAffinity();
                    System.exit(0);
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {});
        runOnUiThread(() -> builder.create().show());
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!stopped) unbindTouchPointer();
    }

    public interface Callback {
        void updateCmdView1(String line);
        void stopPointer();
    }

    private final Callback mCallback = new Callback() {
        public static final int MAX_LINES = 16;
        private int counter1 = 0;
        private final StringBuilder c1 = new StringBuilder();

        public void updateCmdView1(String line) {
            c1.append(line);

            if (counter1 < MAX_LINES) counter1++;
            else c1.delete(0, c1.indexOf("\n") + 1);

            runOnUiThread(() -> binding.cmdview.view1.setText(c1));
        }

        public void stopPointer() {
            MainActivity.this.stopPointer();
        }
    };

    /** Defines callbacks for service binding, passed to bindService() */
    private final ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to Service, cast the IBinder and get TouchPointer instance
            TouchPointer.TouchPointerBinder binder = (TouchPointer.TouchPointerBinder) service;
            pointerOverlay = binder.getService();
            pointerOverlay.activityCallback = mCallback;
            setButtonState(pointerOverlay.cursorView == null);
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        }
    };
}
