import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import machines.Machine;
import machines.MachineType;

import java.util.TreeSet;

public class ExecuteController {

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
    String DESC_DFA;

    @FXML
    String DESC_NFA;

    @FXML
    String DESC_DCA;

    @FXML
    String DESC_DPDA;

    @FXML
    String DESC_ONEWAY;

    @FXML
    String DESC_STANDARD;

    @FXML
    String DESC_MULTITAPE;

    @FXML
    String NOT_LOADED;

    @FXML
    String NO_MACHINE_ERR;

    @FXML
    String BLANK_ERR;

    @FXML
    String DEFAULT_STEP_DELAY;

    @FXML
    String DET_STATE;

    @FXML
    String NON_DET_STATE;


    @FXML
    ChoiceBox<String> choiceTypeToLoad;

    @FXML
    TextField descPath;

    @FXML
    Button descChooseFile;

    @FXML
    Button descPathBtn;

    @FXML
    Label descLoadVerdict;

    @FXML
    Text descFileName;

    @FXML
    ScrollPane descPane;

    @FXML
    Text desc;

    @FXML
    TextField inputString;

    @FXML
    TextField stepDelay;

    @FXML
    Label exeErrorMsg;

    @FXML
    Button startBtn;

    @FXML
    Button stepBtn;

    @FXML
    Button stopBtn;

    @FXML
    Button resetBtn;

    @FXML
    ScrollPane exePane;

    @FXML
    GridPane exeTable;

    @FXML
    Text stateTxt;

    @FXML
    Label state;

    @FXML
    Text steps;


    private TreeSet<String> exeErrors = new TreeSet<>();


    MachineType changeMachineType() {
        String curType = choiceTypeToLoad.getValue();
        if (curType.equals(DFA)) {
            descPath.setText(DESC_DFA);
            return MachineType.DFA;
        } else if (curType.equals(NFA)) {
            descPath.setText(DESC_NFA);
            return MachineType.NFA;
        } else if (curType.equals(DCA)) {
            descPath.setText(DESC_DCA);
            return MachineType.DCA;
        } else if (curType.equals(DPDA)) {
            descPath.setText(DESC_DPDA);
            return MachineType.DPDA;
        } else if (curType.equals(ONEWAY_TM)) {
            descPath.setText(DESC_ONEWAY);
            return MachineType.ONEWAY;
        } else if (curType.equals(STANDARD_TM)) {
            descPath.setText(DESC_STANDARD);
            return MachineType.TURING;
        } else if (curType.equals(MULTITAPE_TM)) {
            descPath.setText(DESC_MULTITAPE);
            return MachineType.MULTITAPE;
        } else throw new AssertionError();
    }

    void setDescFileName(String text) {
        if (text == null)
            text = NOT_LOADED;
        descFileName.setText(text);
    }

    boolean inputHasBlanks(Machine machine) {
        boolean oneCharBlank = machine != null && machine.getBlank() != null && machine.getBlank().length() == 1;
        char b = oneCharBlank? machine.getBlank().charAt(0) : '\0';
        for (char c : inputString.getText().toCharArray()) {
            if (Character.isWhitespace(c) || (oneCharBlank && c == b))
                return true;
        }
        return false;
    }

    double getAndValidateDelay() {
        double delay = 0, newDelay = -1;
        try {
            delay = Double.parseDouble(stepDelay.getText());
            if (delay < 0)
                newDelay = 0;
            if (delay > 1000)
                newDelay = 1000;
        } catch (NumberFormatException e) {
            newDelay = 0;
        }
        if (newDelay != -1) {
            delay = newDelay;
            stepDelay.setText(Double.toString(delay));
        }
        return delay;
    }

    void addExeError(String error) {
        if (exeErrors.add(error))
            showExeError();
    }

    void removeExeError(String error) {
        if (exeErrors.remove(error))
            showExeError();
    }

    private void showExeError() {
        if (exeErrors.isEmpty())
            exeErrorMsg.setText("");
        else
            exeErrorMsg.setText(exeErrors.first());
        exeErrorMsg.setTextFill(Color.RED);
        exeErrorMsg.setVisible(!exeErrors.isEmpty());
    }

    boolean hasNoExeErrors() {
        return exeErrors.isEmpty();
    }

    void updateStatesTxt() {
        stateTxt.setText(choiceTypeToLoad.getValue().equals(NFA)? NON_DET_STATE : DET_STATE);
    }

}