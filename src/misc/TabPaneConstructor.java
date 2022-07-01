package misc;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class TabPaneConstructor {

    private int tabs;
    private String[] labelText;
    private int[] width;
    private Node[] content;

    public TabPaneConstructor(int tabs) {
        this.tabs = tabs;
        labelText = new String[tabs];
        width = new int[tabs];
        content = new Node[tabs];
    }

    public TabPaneConstructor set(int i, String label, int tabWidth, Node node) {
        labelText[i] = label;
        width[i] = tabWidth;
        content[i] = node;
        return this;
    }



    private static final Insets tabInsets = new Insets(8, 0, 0, 0);
    private static final String DEFAULT_PANE = "-fx-background-color: #e4e4ff;";
    private static final String DEFAULT_BACK = "-fx-background-color: #ffffff;";

    public VBox compile() {
        Label[] label = new Label[tabs];

        HBox tabMenu = new HBox(tabs);
        tabMenu.setStyle(DEFAULT_PANE);
        for (int i = 0; i < tabs; i++) {
            label[i] = new Label();
            label[i].setText(labelText[i]);
            updateTabStyle(label[i], i == 0);
            label[i].setAlignment(Pos.CENTER);
            label[i].setMinWidth(width[i]);
            label[i].setMinHeight(32);
            tabMenu.getChildren().add(label[i]);
            HBox.setMargin(label[i], tabInsets);
        }

        VBox vBox = new VBox(2);
        vBox.setStyle(DEFAULT_BACK);
        vBox.getChildren().add(tabMenu);
        vBox.getChildren().add(content[0]);

        for (int i = 0; i < tabs; i++) {
            final int fi = i;
            label[i].setOnMouseClicked(event -> {
                for (int j = 0; j < tabs; j++)
                    updateTabStyle(label[j], j == fi);
                vBox.getChildren().set(1, content[fi]);
            });
        }
        return vBox;
    }

    private static void updateTabStyle(Label tab, boolean isSelected) {
        tab.setStyle(isSelected? DEFAULT_BACK : DEFAULT_PANE);
        tab.setFont(Font.font(
                tab.getFont().getFamily(),
                isSelected? FontWeight.BOLD : FontWeight.NORMAL,
                16
        ));
    }

}
