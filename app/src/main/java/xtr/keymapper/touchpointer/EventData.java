package xtr.keymapper.touchpointer;

import androidx.annotation.Nullable;

import static xtr.keymapper.server.InputService.DOWN;
import static xtr.keymapper.server.InputService.UP;

public class EventData {
    public String code;
    public int action;

    @Nullable
    public static EventData of(String line) {
        EventData event = new EventData();
        // line: EV_KEY KEY_X DOWN
        String[] input_event = line.split("\\s+");
        if (!input_event[1].equals("EV_KEY")) return null;
        event.code = input_event[2];
        if (!event.code.contains("KEY_")) if (!event.code.contains("BTN_")) return null;

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
}
