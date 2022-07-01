import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import machines.*;
import org.fxmisc.richtext.CodeArea;

import java.text.ParseException;

public class EditController {

    @FXML
    String DFA;

    @FXML
    String NFA;

    @FXML
    String DCA;

    @FXML
    String DPDA;

    @FXML
    String ONEWAY_TM;

    @FXML
    String STANDARD_TM;

    @FXML
    String MULTITAPE_TM;



    @FXML
    TextField openPath;

    @FXML
    Button openChooser;

    @FXML
    Button openBtn;

    @FXML
    Label openVerdict;

    @FXML
    VBox editBox;

    @FXML
    CodeArea edit;

    @FXML
    ChoiceBox<String> interpretType;

    Machine initMachine() {
        String curType = interpretType.getValue();
        if (curType.equals(DFA)) {
            return new DFA();
        } else if (curType.equals(NFA)) {
            return new NFA();
        } else if (curType.equals(DCA)) {
            return new DCA();
        } else if (curType.equals(DPDA)) {
            return new DPDA();
        } else if (curType.equals(ONEWAY_TM)) {
            return new OneTM();
        } else if (curType.equals(STANDARD_TM)) {
            return new TM();
        } else if (curType.equals(MULTITAPE_TM)) {
            return new MTM();
        } else throw new AssertionError();
    }

    @FXML
    ScrollPane errorScroll;

    @FXML
    VBox errorBox;

    private static final Color DARK_RED = new Color(0.8, 0, 0, 1);
    private static final Color DARK_ORANGE = new Color(1, 0.4, 0, 1);
    private static final Color ORANGE = new Color(1, 0.5, 0, 1);

    void addErrorsHeader(Label label) {
        label.setText("  Errors");
        label.setTextFill(DARK_RED);
        label.setStyle("-fx-font-weight: bold;");
    }

    void addError(Label label, ParseException error) {
        label.setText(error.getMessage());
        label.setTextFill(Color.RED);
        label.setStyle("-fx-font-weight: normal;");
    }

    void addWarningsHeader(Label label) {
        label.setText("  Warnings");
        label.setTextFill(DARK_ORANGE);
        label.setStyle("-fx-font-weight: bold;");
    }

    void addWarning(Label label, String warning) {
        label.setText(warning);
        label.setTextFill(ORANGE);
        label.setStyle("-fx-font-weight: normal;");
    }

    void addNoErrors(Label label) {
        label.setText("  No errors & no warnings");
        label.setTextFill(Color.DARKCYAN);
        label.setStyle("-fx-font-weight: bold;");
    }

    @FXML
    TextField savePath;

    @FXML
    Button saveChooser;

    @FXML
    Button saveBtn;

    @FXML
    Label saveVerdict;

}
