package misc;

import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public class CustomFileChooser {

    private Stage stage;
    private TextField pathField;
    private Button chooserBtn, actionBtn;

    private FileChooser fc;
    private boolean isOpen;
    private String fcTitle;
    private Consumer<Path> postAction;

    public CustomFileChooser(Stage stage, TextField pathField, Button chooserBtn, Button actionBtn, boolean isOpen) {
        this.stage = stage;
        this.pathField = pathField;
        this.chooserBtn = chooserBtn;
        this.actionBtn = actionBtn;
        this.isOpen = isOpen;
    }

    public CustomFileChooser setDialogTitle(String title) {
        fcTitle = title;
        return this;
    }

    public CustomFileChooser setPostAction(Consumer<Path> postAction) {
        this.postAction = postAction;
        return this;
    }

    public CustomFileChooser compile() {
        fc = new FileChooser();
        fc.setTitle(fcTitle);
        updateChooserInitPath();

        pathField.setOnKeyPressed(event -> {
            if (event.getCode().equals(KeyCode.ENTER)) {
                runPostAction();
            }
        });

        pathField.setOnKeyReleased(event -> updateChooserInitPath());

        chooserBtn.setOnMouseClicked(event -> {
            File file = isOpen? fc.showOpenDialog(stage) : fc.showSaveDialog(stage);
            if (file != null) {
                pathField.setText(file.getAbsolutePath());
                updateChooserInitPath();
                runPostAction();
            }
        });

        actionBtn.setOnMouseClicked(event -> runPostAction());

        return this;
    }

    public void updateChooserInitPath() {
        Path path = Path.of(pathField.getText());
        if (!Files.isDirectory(path)) {
            if (!Files.exists(path)) {
                path = null;
            } else {
                path = path.getParent();
                if (!Files.isDirectory(path))
                    path = null;
            }
        }
        updateChooserInitPath(path);
    }

    public void updateChooserInitPath(Path path) {
        if (path != null)
            fc.setInitialDirectory(path.toFile());
    }



    private void runPostAction() {
        Path path = Path.of(pathField.getText());
        if (!path.isAbsolute()) {
            path = Path.of(System.getProperty("user.dir"), path.toString());
        }
        postAction.accept(path);
    }

}
