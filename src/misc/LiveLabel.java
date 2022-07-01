package misc;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

public class LiveLabel {

    private static final List<LiveLabel> registry = new ArrayList<>();
    private static final Lock registryLock = new Lock();
    private static final int UPDATE_LABELS_DELAY = 4000;

    public static void runRegistry() {
        Thread thread = new Thread(() -> {
            while (true) {

                try {
                    Thread.sleep(UPDATE_LABELS_DELAY);
                } catch (InterruptedException ignored) { }

                registryLock.doWithLock(() -> Platform.runLater(() -> {
                    for (LiveLabel ll : registry) {
                        long time = System.currentTimeMillis() - ll.lastUpdate;
                        String timeStr = timeStr(time);
                        if (!timeStr.equals(ll.timeStrMem)) {
                            ll.timeStrMem = timeStr;
                            ll.label.setText(ll.text + "  (" + timeStr + " ago)");
                        }
                    }
                }));
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private static String timeStr(long time) {
        if (time < 1000)
            return time + " ms";
        else if (time < 60_000)
            return (time / 1000) + " s";
        else if (time < 3_600_000)
            return (time / 60_000) + " min";
        else
            return (time / 3_600_000) + " h";
    }



    private Label label;
    private String text = "";
    private long lastUpdate = System.currentTimeMillis();
    private boolean registered = false;
    private String timeStrMem = "-";

    public LiveLabel(Label label) {
        this.label = label;
    }

    public void setText(String text, int success) {
        this.text = text;
        label.setText(text + "  (moments ago)");
        label.setTextFill(success == 1? Color.LIMEGREEN : success == 0? Color.ORANGE : Color.RED);
        timeStrMem = "now";
        lastUpdate = System.currentTimeMillis();
        if (!registered) {
            registered = true;
            registryLock.doWithLock(() -> registry.add(this));
        }
    }

}
