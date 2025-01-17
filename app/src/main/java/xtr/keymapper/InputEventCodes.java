package xtr.keymapper;

import java.util.List;
import java.util.Map;

// REF: linux/input-event-codes.h
public class InputEventCodes {
    public static final int REL_X = 0;
    public static final int REL_Y = 1;
    public static final int REL_WHEEL = 8;
    public static final int BTN_MOUSE = 0x110;
    public static final int BTN_RIGHT = 0x111;
    public static final int BTN_MIDDLE = 0x112;
    public static final int BTN_SIDE = 0x113;
    public static final int BTN_EXTRA = 0x114;
    public static final String[] ARROW_KEYS = { "KEY_UP", "KEY_DOWN", "KEY_LEFT", "KEY_RIGHT" };
    public static final String[] WASD_KEYS = { "KEY_W", "KEY_S", "KEY_A", "KEY_D" };

    public static final Map<String, Integer> KNOWN_INPUT = Map.of(
            "REL_X", REL_X,
            "REL_Y", REL_Y,
            "REL_WHEEL", REL_WHEEL,
            "BTN_MOUSE", BTN_MOUSE,
            "BTN_LEFT", BTN_MOUSE,
            "BTN_RIGHT", BTN_RIGHT,
            "BTN_MIDDLE", BTN_MIDDLE,
            "BTN_SIDE", BTN_SIDE,
            "BTN_EXTRA", BTN_EXTRA
    );

    public static final List<String> VALID_BTN_TO_TOUCH = List.of("BTN_MIDDLE", "BTN_SIDE", "BTN_EXTRA");
}
