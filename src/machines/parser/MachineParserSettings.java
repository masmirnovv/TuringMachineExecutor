package machines.parser;

import java.util.function.Consumer;
import java.util.function.Function;

public class MachineParserSettings {

    private String key;
    private Function<String[], ParseVerdict> action;

    public MachineParserSettings(String key, Function<String, ParseVerdict> action) {
        this.key = key + ":";
        this.action = args -> action.apply(args[1]);
    }

    public MachineParserSettings(String key, Consumer<String[]> action) {
        this.key = key + ":";
        this.action = args -> {action.accept(args); return ParseVerdict.OK;};
    }

    String getKey() {
        return key;
    }

    Function<String[], ParseVerdict> getAction() {
        return action;
    }

}
