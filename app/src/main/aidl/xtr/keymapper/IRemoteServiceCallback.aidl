package xtr.keymapper;

import xtr.keymapper.keymap.KeymapConfig;
import xtr.keymapper.keymap.KeymapProfile;

interface IRemoteServiceCallback {
    void launchEditor();
    void alertMouseAimActivated();
    KeymapProfile requestKeymapProfile();
    KeymapConfig requestKeymapConfig();
    void switchProfiles();
}