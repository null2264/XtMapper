package xtr.keymapper.touchpointer;

import androidx.annotation.Nullable;

import java.util.Optional;

import static xtr.keymapper.InputEventCodes.KNOWN_INPUT;
import static xtr.keymapper.server.InputService.DOWN;
import static xtr.keymapper.server.InputService.UP;

public class InputEvent {
    public String code;
    public int action;

    private static Boolean isValidInput(String type, String code) {
        return (type.equals("EV_KEY") || type.equals("EV_REL") || type.equals("EV_ABS")) &&
                (code.contains("KEY_") || code.contains("BTN_") || code.contains("ABS_") || code.contains("REL_"));
    }

    @Nullable
    public static InputEvent of(String line) {
        InputEvent event = new InputEvent();
        // line: EV_KEY KEY_X DOWN
        String[] input_event = line.split("\\s+");
        if (!isValidInput(input_event[1], input_event[2])) return null;
        event.code = input_event[2];


        if (event.code.contains("REL_") || event.code.contains("ABS_")) {
            event.action = Integer.parseInt(input_event[3]);
            return event;
        }

        switch (input_event[3]) {
            case "0":
            case "UP":
                event.action = UP;
                break;
            case "1":
            case "DOWN":
                event.action = DOWN;
                break;
            default:
                return null;
        }
        return event;
    }

    public Optional<Integer> codeInt() {
        try {
            return Optional.ofNullable(KNOWN_INPUT.get(this.code));
        } catch (NullPointerException exc) {
            return Optional.empty();
        }
    }
}
