package xtr.keymapper.touchpointer;

import static xtr.keymapper.keymap.KeymapConfig.KEY_ALT;
import static xtr.keymapper.keymap.KeymapConfig.KEY_CTRL;
import static xtr.keymapper.server.InputService.DOWN;
import static xtr.keymapper.server.InputService.UP;
import static xtr.keymapper.touchpointer.PointerId.dpadpid1;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;

import java.util.ArrayList;

import xtr.keymapper.Utils;
import xtr.keymapper.dpad.Dpad;
import xtr.keymapper.dpad.DpadHandler;
import xtr.keymapper.keymap.KeymapConfig;
import xtr.keymapper.keymap.KeymapProfile;
import xtr.keymapper.keymap.KeymapProfileKey;
import xtr.keymapper.server.IInputInterface;
import xtr.keymapper.swipekey.SwipeKey;
import xtr.keymapper.swipekey.SwipeKeyHandler;

public class KeyEventHandler {
    public boolean ctrlKeyPressed = false;
    public boolean altKeyPressed = false;
    private DpadHandler[] dpadHandlers;
    private ArrayList<SwipeKeyHandler> swipeKeyHandlers;
    private final PidProvider pidProvider = new PidProvider();
    private final IInputInterface mInput;
    private HandlerThread mHandlerThread;
    private Handler eventHandler;

    public KeyEventHandler(IInputInterface mInput) {
        this.mInput = mInput;
    }

    public void init(){
        mHandlerThread = new HandlerThread("events");
        mHandlerThread.start();
        eventHandler = new Handler(mHandlerThread.getLooper());

        KeymapConfig keymapConfig = mInput.getKeymapConfig();
        KeymapProfile profile = mInput.getKeymapProfile();


        dpadHandlers = new DpadHandler[Dpad.MAX_DPADS + 1];
        for (int i = 0; i < dpadHandlers.length; i++) {
            int pid = dpadpid1.id + i;
            if ( i >= 2 ) { // Arrow keys
                if (profile.dpadUdlr != null) {
                    dpadHandlers[i] = new DpadHandler(keymapConfig.dpadRadiusMultiplier, profile.dpadUdlr, pid, eventHandler, keymapConfig.swipeDelayMs);
                    dpadHandlers[i].setInterface(mInput);
                }
            } else if (profile.dpadArray[i] != null) {
                dpadHandlers[i] = new DpadHandler(keymapConfig.dpadRadiusMultiplier, profile.dpadArray[i], pid, eventHandler, keymapConfig.swipeDelayMs);
                dpadHandlers[i].setInterface(mInput);
            }
        }


        // Correction of x and y deviation from center
        for (KeymapProfileKey key: profile.keys) {
            key.x += key.offset;
            key.y += key.offset;
        }

        swipeKeyHandlers = new ArrayList<>();
        for (SwipeKey key : profile.swipeKeys) {
            swipeKeyHandlers.add(new SwipeKeyHandler(key));
        }
    }

    public void stop() {
        dpadHandlers = null;
        swipeKeyHandlers = null;
        if (mHandlerThread != null)
            mHandlerThread.quit();
        mHandlerThread = null;
        eventHandler = null;
    }

    public void handleEvent(String line) throws RemoteException {
        // line: EV_KEY KEY_X DOWN
        EventData event = EventData.of(line);
        if (event == null) return;

        KeymapConfig keymapConfig = mInput.getKeymapConfig();

        detectCtrlAltKeys(event);
        int i = Utils.obtainIndex(event.code);
        if (i > 0) { // A-Z and 0-9 keys
            if (event.action == DOWN) handleKeyboardShortcuts(i);
            handleMouseAim(i, event.action);
        } else { // CTRL, ALT, Arrow keys
            if (event.code.equals("KEY_GRAVE") && event.action == DOWN)
                if (keymapConfig.keyGraveMouseAim)
                    mInput.getMouseEventHandler().triggerMouseAim();
        }

        for (DpadHandler dpadHandler: dpadHandlers) {
            if (dpadHandler != null)
                dpadHandler.handleEvent(event.code, event.action);
        }

        handleTouch(event);

        for (SwipeKeyHandler swipeKeyHandler : swipeKeyHandlers)
            swipeKeyHandler.handleEvent(event, mInput, pidProvider, eventHandler, keymapConfig.swipeDelayMs);
    }

    public void handleTouch(EventData event) {
        ArrayList<KeymapProfileKey> keyList = mInput.getKeymapProfile().keys;
        for (KeymapProfileKey key : keyList)
            if (event.code.equals(key.code))
                mInput.injectEvent(key.x, key.y, event.action, keyList.indexOf(key));
    }

    private void detectCtrlAltKeys(EventData event) {
        if (event.code.contains("CTRL")) ctrlKeyPressed = event.action == DOWN;
        if (event.code.contains("ALT")) altKeyPressed = event.action == DOWN;
    }

    private void handleKeyboardShortcuts(int keycode) throws RemoteException {
        if (!(altKeyPressed || ctrlKeyPressed)) return;
        final String modifier = ctrlKeyPressed ? KEY_CTRL : KEY_ALT;
        KeymapConfig keymapConfig = mInput.getKeymapConfig();

        if (keymapConfig.launchEditorShortcutKeyModifier.equals(modifier))
            if (keycode == keymapConfig.launchEditorShortcutKey)
                mInput.getCallback().launchEditor();

        if (keymapConfig.pauseResumeShortcutKeyModifier.equals(modifier))
            if (keycode == keymapConfig.pauseResumeShortcutKey)
                mInput.pauseResumeKeymap();

        if (keymapConfig.switchProfileShortcutKeyModifier.equals(modifier))
            if (keycode == keymapConfig.switchProfileShortcutKey)
                mInput.getCallback().switchProfiles();
    }

    public void handleKeyboardShortcutEvent(String line) throws RemoteException {
        EventData event = EventData.of(line);
        if (event != null) {
            detectCtrlAltKeys(event);
            int i = Utils.obtainIndex(event.code);
            if (event.action == DOWN) handleKeyboardShortcuts(i);
        }
    }

    private void handleMouseAim(int keycode, int action) {
        KeymapConfig keymapConfig = mInput.getKeymapConfig();
        if (keycode == keymapConfig.mouseAimShortcutKey)
            if (action == DOWN && keymapConfig.mouseAimToggle) mInput.getMouseEventHandler().triggerMouseAim();
            else mInput.getMouseEventHandler().triggerMouseAim();
    }
}
