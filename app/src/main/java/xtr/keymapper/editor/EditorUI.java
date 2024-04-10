package xtr.keymapper.editor;

import static xtr.keymapper.InputEventCodes.VALID_BTN_TO_TOUCH;
import static xtr.keymapper.Utils.stripIfKey;
import static xtr.keymapper.dpad.Dpad.MAX_DPADS;
import static xtr.keymapper.keymap.KeymapProfiles.MOUSE_RIGHT;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import xtr.keymapper.InputEventCodes;
import xtr.keymapper.OnKeyEventListener;
import xtr.keymapper.R;
import xtr.keymapper.databinding.CrosshairBinding;
import xtr.keymapper.databinding.DpadArrowsBinding;
import xtr.keymapper.databinding.DpadBinding;
import xtr.keymapper.databinding.KeymapEditorBinding;
import xtr.keymapper.databinding.ResizableBinding;
import xtr.keymapper.dpad.Dpad;
import xtr.keymapper.dpad.DpadKeyCodes;
import xtr.keymapper.floatingkeys.MovableFloatingActionKey;
import xtr.keymapper.floatingkeys.MovableFrameLayout;
import xtr.keymapper.keymap.KeymapProfile;
import xtr.keymapper.keymap.KeymapProfileKey;
import xtr.keymapper.keymap.KeymapProfiles;
import xtr.keymapper.mouse.MouseAimConfig;
import xtr.keymapper.mouse.MouseAimSettings;
import xtr.keymapper.server.RemoteServiceHelper;
import xtr.keymapper.swipekey.SwipeKey;
import xtr.keymapper.swipekey.SwipeKeyView;
import xtr.keymapper.touchpointer.InputEvent;

public class EditorUI extends OnKeyEventListener.Stub {

    private final LayoutInflater layoutInflater;
    private final ViewGroup mainView;

    private KeyInFocus keyInFocus;
    // Keyboard keys
    private final List<MovableFloatingActionKey> keyList = new ArrayList<>();
    private final List<SwipeKeyView> swipeKeyList = new ArrayList<>();
    private MovableFloatingActionKey leftClick, rightClick;

    private MovableFrameLayout crosshair;
    private final MovableFrameLayout[] dpadArray = new MovableFrameLayout[MAX_DPADS];
    private final DpadBinding[] dpadBindingArray = new DpadBinding[MAX_DPADS];
    // Default position of new views added
    private final KeymapEditorBinding binding;
    private final Context context;
    private final OnHideListener onHideListener;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final String profileName;
    private KeymapProfile profile;
    private boolean overlayOpen = false;
    private MovableFrameLayout dpadUdlr;

    interface KeyInFocus {
        void setText(String key);
    }

    public EditorUI (Context context, OnHideListener onHideListener, String profileName) {
        this.context = context;
        this.onHideListener = onHideListener;
        this.profileName = profileName;

        layoutInflater = context.getSystemService(LayoutInflater.class);

        binding = KeymapEditorBinding.inflate(layoutInflater);
        mainView = binding.getRoot();

        binding.speedDial.inflate(R.menu.keymap_editor_menu);
        binding.speedDial.open();
        setupButtons();
    }

    public void open(boolean overlayWindow) {
        loadKeymap();
        if (mainView.getWindowToken() == null && mainView.getParent() == null)
            if (overlayWindow) openOverlayWindow();
            else ((EditorActivity)context).setContentView(mainView);

        if (!onHideListener.getEvent()) {
            mainView.setOnKeyListener(this::onKey);
            mainView.setFocusable(true);
        }
    }

    public void openOverlayWindow() {
        if (overlayOpen) removeView(mainView);
        WindowManager mWindowManager = context.getSystemService(WindowManager.class);
        WindowManager.LayoutParams mParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        mWindowManager.addView(mainView, mParams);
        overlayOpen = true;
    }

    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (keyInFocus != null) {
            String key = String.valueOf(event.getDisplayLabel());
            if ( key.matches("[a-zA-Z0-9]+" )) {
                keyInFocus.setText(key);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onKeyEvent(String line) {
        // line: /dev/input/event3 EV_KEY KEY_X DOWN
        InputEvent event = InputEvent.of(line);

        // Ignore invalid events
        if (event == null) return;
        if (!event.code.startsWith("KEY_") && !VALID_BTN_TO_TOUCH.contains(event.code)) return;

        // Incoming calls are not guaranteed to be executed on the main thread
        mHandler.post(() -> {
            if (keyInFocus != null)
                keyInFocus.setText(event.code);
        });
    }


    public interface OnHideListener {
        void onHideView();
        boolean getEvent();
    }

    public void hideView() {
        saveKeymap();
        removeView(mainView);
        onHideListener.onHideView();
    }

    private void removeView(ViewGroup view) {
        if (overlayOpen) context.getSystemService(WindowManager.class).removeView(view);
        view.removeAllViews();
        view.invalidate();
    }

    private void loadKeymap() {
        profile = new KeymapProfiles(context).getProfile(profileName);
        // Add Keyboard keys as Views
        profile.keys.forEach(this::addKey);
        profile.swipeKeys.forEach(swipeKey -> swipeKeyList.add(new SwipeKeyView(mainView, swipeKey, swipeKeyList::remove, this::onClick)));


        for (int i = 0; i < profile.dpadArray.length; i++)
            if (profile.dpadArray[i] != null)
                addDpad(i, profile.dpadArray[i].getX(), profile.dpadArray[i].getY());

        if (profile.dpadUdlr != null) addArrowKeysDpad(profile.dpadUdlr.getX(), profile.dpadUdlr.getY());

        if (profile.mouseAimConfig != null) addCrosshair(profile.mouseAimConfig.xCenter, profile.mouseAimConfig.yCenter);
        if (profile.rightClick != null) addRightClick(profile.rightClick.x, profile.rightClick.y);
    }

    private void saveKeymap() {
        ArrayList<String> linesToWrite = new ArrayList<>();

        for (int i = 0; i < dpadArray.length; i++)
            if (dpadArray[i] != null) {
                Dpad dpad = new Dpad(dpadArray[i], new DpadKeyCodes(dpadBindingArray[i]), Dpad.TAG);
                linesToWrite.add(dpad.getData());
            }

        if (dpadUdlr != null) {
            Dpad dpad = new Dpad(dpadUdlr, new DpadKeyCodes(InputEventCodes.ARROW_KEYS), Dpad.UDLR);
            linesToWrite.add(dpad.getData());
        }

        if (crosshair != null) {
            // Get x and y coordinates from view
            profile.mouseAimConfig.setCenterXY(crosshair);
            profile.mouseAimConfig.setLeftClickXY(leftClick);
            linesToWrite.add(profile.mouseAimConfig.getData());
        }

        if (rightClick != null) {
            linesToWrite.add(MOUSE_RIGHT + " " + rightClick.getX() + " " + rightClick.getY());
        }
        
        // Keyboard keys
        keyList.stream().map(MovableFloatingActionKey::getData).forEach(linesToWrite::add);

        swipeKeyList.stream().map(swipeKeyView -> new SwipeKey(swipeKeyView).getData()).forEach(linesToWrite::add);

        // Save Config
        KeymapProfiles profiles = new KeymapProfiles(context);
        profiles.saveProfile(profileName, linesToWrite, profile.packageName, !profile.disabled);

        // Reload keymap if service running
        RemoteServiceHelper.reloadKeymap(context);
    }


    public void setupButtons() {
        binding.speedDial.setOnActionSelectedListener(actionItem -> {
            // X y coordinates of center of root view
            float defaultX = mainView.getPivotX();
            float defaultY = mainView.getPivotY();

            int id = actionItem.getId();

            if (id == R.id.add) {
                addKey(defaultX, defaultY);
            }
            else if (id == R.id.save) {
                hideView();
            }
            else if (id == R.id.dpad) {
                final CharSequence[] items = { "Arrow keys", "WASD Keys", "Custom"};
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
                builder.setTitle("Select Dpad").setItems(items, (dialog, i) -> {
                    if (i == 0) addArrowKeysDpad(defaultX, defaultY);
                    else if (i == 1) addWasdDpad(defaultX, defaultY);
                    else addDpad(getNextDpadId(), defaultX, defaultY);
                });
                AlertDialog dialog = builder.create();
                if (overlayOpen) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                dialog.show();
            }
            else if (id == R.id.crosshair) {
                profile.mouseAimConfig = new MouseAimConfig();
                addCrosshair(defaultX, defaultY);
            }
            else if (id == R.id.mouse_left) {
                addLeftClick(defaultX, defaultY);
            }
            else if (id == R.id.swipe_key) {
                addSwipeKey();
            }
            else if (id == R.id.mouse_right) {
                addRightClick(defaultX, defaultY);
            }
            return true;
        });
    }

    private void addWasdDpad(float defaultX, float defaultY) {
        int i = getNextDpadId();
        addDpad(i, defaultX, defaultY);
        profile.dpadArray[i] = new Dpad(dpadArray[i], new DpadKeyCodes(InputEventCodes.WASD_KEYS), Dpad.TAG);
        addDpad(i, defaultX, defaultY);
    }

    private int getNextDpadId() {
        for (int i = 0; i < dpadArray.length; i++) {
            if (dpadArray[i] == null) return i;
        }
        return 0;
    }


    private void addArrowKeysDpad(float x, float y) {
        if (dpadUdlr == null) {
            DpadArrowsBinding binding = DpadArrowsBinding.inflate(layoutInflater, mainView, true);
            dpadUdlr = binding.getRoot();

            binding.closeButton.setOnClickListener(v -> {
                mainView.removeView(dpadUdlr);
                dpadUdlr = null;
            });
            binding.resizeHandle.setOnTouchListener(new ResizeableDpadView(dpadUdlr));
        }
        moveResizeDpad(dpadUdlr, profile.dpadUdlr, x, y);
    }

    private void addDpad(int i, float x, float y) {
        if (dpadArray[i] == null) {
            dpadBindingArray[i] = DpadBinding.inflate(layoutInflater, mainView, true);
            dpadArray[i] = dpadBindingArray[i].getRoot();

            dpadBindingArray[i].closeButton.setOnClickListener(v -> {
                mainView.removeView(dpadArray[i]);
                dpadArray[i] = null;
            });
            dpadBindingArray[i].resizeHandle.setOnTouchListener(new ResizeableDpadView(dpadArray[i]));
        }
        setDpadKeys(dpadBindingArray[i], profile.dpadArray[i]);
        moveResizeDpad(dpadArray[i], profile.dpadArray[i], x, y);
    }

    private void setDpadKeys(DpadBinding binding, Dpad dpad) {
        if (dpad != null) { // strip KEY_
            binding.keyUp.setText(stripIfKey(dpad.keycodes.Up));
            binding.keyDown.setText(stripIfKey(dpad.keycodes.Down));
            binding.keyLeft.setText(stripIfKey(dpad.keycodes.Left));
            binding.keyRight.setText(stripIfKey(dpad.keycodes.Right));
        }
        for (TextView key : new TextView[]{binding.keyUp, binding.keyDown, binding.keyRight, binding.keyLeft}) {
            key.setOnClickListener(
                    view -> keyInFocus = k -> ((TextView)view).setText(stripIfKey(k)));
        }
    }

    private void moveResizeDpad(ViewGroup dpadLayout, Dpad dpad, float x, float y) {
        dpadLayout.animate().x(x).y(y)
                .setDuration(500)
                .start();

        if (dpad != null) {
            // resize dpad from saved profile configuration
            float x1 = dpad.getWidth() - dpadLayout.getLayoutParams().width;
            float y1 = dpad.getHeight() - dpadLayout.getLayoutParams().height;
            resizeView(dpadLayout, x1, y1);
        }
    }

    private void addKey(KeymapProfileKey key) {
        MovableFloatingActionKey floatingKey = new MovableFloatingActionKey(context, keyList::remove);

        floatingKey.setText(key.code);
        floatingKey.animate()
                .x(key.x)
                .y(key.y)
                .setDuration(1000)
                .start();
        floatingKey.setOnClickListener(this::onClick);

        mainView.addView(floatingKey);
        keyList.add(floatingKey);
    }

    private void addKey(float x, float y) {
        final KeymapProfileKey key = new KeymapProfileKey();
        key.code = "KEY_X";
        key.x = x;
        key.y = y;
        addKey(key);
    }

    public void onClick(View view) {
        keyInFocus = key -> ((MovableFloatingActionKey)view).setText(key);
    }

    private void addCrosshair(float x, float y) {
        if (crosshair == null) {
            CrosshairBinding binding = CrosshairBinding.inflate(layoutInflater, mainView, true);
            crosshair = binding.getRoot();

            binding.closeButton.setOnClickListener(v -> {
                mainView.removeView(crosshair);
                crosshair = null;
            });
            binding.expandButton.setOnClickListener(v -> {
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
                CharSequence[] list = {"Limit to specified area", "Allow moving pointer out of screen"};
                // Set the dialog title
                builder.setTitle("Adjust bounds")
                        .setItems(list, (dialog, which) -> {
                            profile.mouseAimConfig.width = 0;
                            profile.mouseAimConfig.height = 0;
                            if (which == 0) {
                                profile.mouseAimConfig.limitedBounds = true;
                                new ResizableArea();
                            } else {
                                profile.mouseAimConfig.limitedBounds = false;
                            }
                        });
                AlertDialog dialog = builder.create();
                if(overlayOpen) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                dialog.show();
            });
            binding.editButton.setOnClickListener(v -> MouseAimSettings.getKeyDialog(context).show());
        }
        crosshair.animate().x(x).y(y)
                .setDuration(500)
                .start();

        addLeftClick(profile.mouseAimConfig.xleftClick,
                     profile.mouseAimConfig.yleftClick);
    }

    private void addLeftClick(float x, float y) {
        if (leftClick == null) {
            leftClick = new MovableFloatingActionKey(context);
            leftClick.key.setImageResource(R.drawable.ic_baseline_mouse_36);
            mainView.addView(leftClick);
        }
        leftClick.animate().x(x).y(y)
                .setDuration(500)
                .start();
    }

    private void addRightClick(float x, float y) {
        if (rightClick == null) {
            rightClick = new MovableFloatingActionKey(context, key -> rightClick = null);
            rightClick.key.setImageResource(R.drawable.ic_baseline_mouse_36);
            mainView.addView(rightClick);
        }
        rightClick.animate().x(x).y(y)
                .setDuration(500)
                .start();
    }

    private void addSwipeKey() {
        SwipeKeyView swipeKeyView = new SwipeKeyView(mainView, swipeKeyList::remove, this::onClick);
        swipeKeyList.add(swipeKeyView);
    }

    public static void resizeView(View view, float x, float y) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        layoutParams.width += x;
        layoutParams.height += y;
        view.requestLayout();
    }

    class ResizableArea implements View.OnTouchListener, View.OnClickListener {
        private final ViewGroup rootView;
        private float defaultPivotX, defaultPivotY;

        @SuppressLint("ClickableViewAccessibility")
        public ResizableArea(){
            ResizableBinding binding1 = ResizableBinding.inflate(layoutInflater, mainView, true);
            rootView = binding1.getRoot();
            binding1.dragHandle.setOnTouchListener(this);
            binding1.saveButton.setOnClickListener(this);
            moveView();
        }

        private void getDefaultPivotXY(){
            defaultPivotX = rootView.getPivotX();
            defaultPivotY = rootView.getPivotY();
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                resizeView(rootView, event.getX(), event.getY());
                // Resize View from center point
                if (defaultPivotX > 0) {
                    float newPivotX = rootView.getPivotX() - defaultPivotX;
                    float newPivotY = rootView.getPivotY() - defaultPivotY;
                    rootView.setX(rootView.getX() - newPivotX);
                    rootView.setY(rootView.getY() - newPivotY);
                }
                getDefaultPivotXY();
            } else
                v.performClick();
            return true;
        }
        @Override
        public void onClick(View v) {
            float x = rootView.getX() + rootView.getPivotX();
            float y = rootView.getY() + rootView.getPivotY();
            crosshair.setX(x);
            crosshair.setX(x);
            crosshair.setY(y);
            profile.mouseAimConfig.width = rootView.getPivotX();
            profile.mouseAimConfig.height = rootView.getPivotY();

            mainView.removeView(rootView);
            rootView.invalidate();
        }
        private void moveView(){
            float x = crosshair.getX() - crosshair.getWidth();
            float y = crosshair.getY() - crosshair.getHeight();
            rootView.setX(x);
            rootView.setY(y);
        }
    }
}
