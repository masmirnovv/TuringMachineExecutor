package misc;

import javafx.scene.Node;
import javafx.scene.paint.Color;

public class Colors {

    public static final Color EXE_NONE = Color.rgb(0xff, 0xff, 0xff);
    public static final Color EXE_BLANK = Color.rgb(0xee, 0xee, 0xee);
    public static final Color EXE_DEFAULT = Color.rgb(0xc0, 0xc0, 0xff);
    public static final Color EXE_RED = Color.rgb(0xed, 0x5e, 0x5e);
    public static final Color EXE_LIME = Color.rgb(0x70, 0xfa, 0x70);
    public static final Color EXE_LIGHTER_LIME = Color.rgb(0xa0, 0xfd, 0xa0);


    public static void setColor(Node node, Color color) {
        node.setStyle("-fx-background-color: #" + hex(scale255(color.getRed())) + hex(scale255(color.getGreen())) + hex(scale255(color.getBlue())));
    }



    private static int scale255(double a) {
        return (int) Math.round(a * 255);
    }

    private static String hex(int val) {
        return "" + toHex(val / 16) + toHex(val % 16);
    }

    private static char toHex(int n) {
        if (n < 0 || n > 15)
            throw new AssertionError();
        if (n < 10)
            return (char) ('0' + n);
        else
            return (char) ('a' + n - 10);
    }

}
