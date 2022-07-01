import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import machines.Machine;
import machines.parser.ParseVerdict;
import misc.CustomFileChooser;
import misc.LiveLabel;
import misc.Lock;
import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.Paragraph;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

class EditTab {

    private static final String OPEN_TITLE = "Open file";
    private static final String SAVE_TITLE = "Save file";
    private static final Path DEFAULT_PATH = Path.of("src", "desc");

    private static final String LN = System.lineSeparator();

    private static final Insets ERROR_INSETS = new Insets(3, 3, 3, 3);

    private EditController edc;
    private Postpone postpone;
    private Lock errorPaneLock = new Lock();

    private LiveLabel openVerdict, saveVerdict;

    EditTab(EditController edc, Stage stage) {
        this.edc = edc;
        this.postpone = new Postpone(this::updateErrorsAndWarnings);

        edc.errorBox.maxWidthProperty().bind(edc.errorScroll.widthProperty());
        edc.edit.prefHeightProperty().bind(edc.editBox.heightProperty());

        edc.edit.setParagraphGraphicFactory(lineNumberFactory(edc.edit));

        CustomFileChooser openFc = new CustomFileChooser(stage, edc.openPath, edc.openChooser, edc.openBtn, true)
                .setDialogTitle(OPEN_TITLE)
                .setPostAction(this::openFile)
                .compile();
        CustomFileChooser saveFc = new CustomFileChooser(stage, edc.savePath, edc.saveChooser, edc.saveBtn, false)
                .setDialogTitle(SAVE_TITLE)
                .setPostAction(this::saveFile)
                .compile();
        openFc.updateChooserInitPath(DEFAULT_PATH);
        saveFc.updateChooserInitPath(DEFAULT_PATH);

        openVerdict = new LiveLabel(edc.openVerdict);
        saveVerdict = new LiveLabel(edc.saveVerdict);
    }

    void postInit() {
        addListeners();
        Main.fixBlurryText(edc.errorScroll);
    }



    private void addListeners() {
        edc.edit.setOnKeyTyped(event -> postpone.on(500));
        edc.interpretType.setOnAction(event -> updateErrorsAndWarnings());
    }



    private void updateErrorsAndWarnings() {
        errorPaneLock.doWithLock(() -> {
            Machine machine = edc.initMachine();
            ParseVerdict verdict = machine.parse(edc.edit.getText());

            int oldSize = edc.errorBox.getChildren().size();
            int newSize = Math.max(1,
                    (verdict.hasErrors()? 1 : 0)
                    + verdict.getErrors().size()
                    + (verdict.hasWarnings()? 1 : 0)
                    + verdict.getWarnings().size());
            Platform.runLater(() -> updateErrorsAndWarningsSize(oldSize, newSize));

            AtomicInteger i = new AtomicInteger();
            ObservableList<Node> labels = edc.errorBox.getChildren();

            if (!verdict.hasErrors() && !verdict.hasWarnings()) {
                Platform.runLater(() -> edc.addNoErrors((Label) labels.get(i.getAndIncrement())));
            }
            if (verdict.hasErrors()) {
                Platform.runLater(() -> {
                    edc.addErrorsHeader((Label) labels.get(i.getAndIncrement()));
                    for (ParseException error : verdict.getErrors())
                        edc.addError((Label) labels.get(i.getAndIncrement()), error);
                });
            }
            if (verdict.hasWarnings()) {
                Platform.runLater(() -> {
                    edc.addWarningsHeader((Label) labels.get(i.getAndIncrement()));
                    for (String warning : verdict.getWarnings())
                        edc.addWarning((Label) labels.get(i.getAndIncrement()), warning);
                });
            }
        });
    }

    private void updateErrorsAndWarningsSize(int oldSize, int newSize) {
        switch (Integer.compare(oldSize, newSize)) {
            case -1:
                for (int i = oldSize; i < newSize; i++) {
                    Label label = new Label();
                    label.setWrapText(true);
                    VBox.setMargin(label, ERROR_INSETS);
                    edc.errorBox.getChildren().add(label);
                }
                break;
            case 1:
                edc.errorBox.getChildren().subList(newSize, oldSize).clear();
                break;
        }
    }



    private void openFile(Path path) {
        try {
            String content = Files.readString(path);
            edc.edit.replaceText(content);
            openVerdict.setText("Opened", 1);
            updateErrorsAndWarnings();
        } catch (IOException e) {
            openVerdict.setText("Invalid path:  " + path, -1);
        }
    }

    private void saveFile(Path path) {
        try (FileWriter writer = new FileWriter(path.toFile())) {
            boolean firstLine = true;
            for (Paragraph<Collection<String>, String, Collection<String>> paragraph : edc.edit.getParagraphs()) {
                writer.write(firstLine? "" : LN);
                writer.write(paragraph.getText());
                firstLine = false;
            }
            saveVerdict.setText("Saved", 1);
        } catch (IOException e) {
            saveVerdict.setText("Invalid path:  " + path, -1);
        }
    }

    private static IntFunction<Node> lineNumberFactory(GenericStyledArea<?, ?, ?> area) {
        return ln -> {
            Label label = (Label) LineNumberFactory.get(area).apply(ln);
            label.setFont(Font.font("Droid Sans Mono Dotted"));
            label.setStyle("-fx-background-color: #e4e4ff;");
            return label;
        };
    }



    private static class Postpone {

        private long timeToDo = System.currentTimeMillis() - 1;
        private boolean done = true;
        private Lock lock = new Lock();

        Postpone(Runnable actionToDo) {
            Thread thread = new Thread(() -> {
                while (true) {
                    lock.doWithLock(() -> {
                        if (!done && System.currentTimeMillis() >= timeToDo) {
                            done = true;
                            actionToDo.run();
                        }
                    });
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        void on(long timeMillis) {
            lock.doWithLock(() -> {
                this.done = false;
                timeToDo = System.currentTimeMillis() + timeMillis;
            });
        }

    }

}
