import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import machines.*;
import machines.parser.ParseVerdict;
import misc.Colors;
import misc.CustomFileChooser;
import misc.LiveLabel;
import misc.Lock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

class ExecuteTab {

    private static final String DESC_FC_TITLE = "Choose machine description file";

    private static final double SMALL_MARGIN = 4;

    private static final int MAX_MACHINE_FPS = 20;
    private static final int MAX_TAPE_LENGTH = 200;

    private ExecuteController ec;

    private CustomFileChooser descFc;
    private LiveLabel descLoadVerdict;

    private List<FlowPane> exeTableSymPanes;
    private List<Text> exeTableSyms;

    private MachineType machineType = MachineType.TURING;
    private Machine machine = null;
    private Lock machineBusy = new Lock();
    private Lock machineUILock = new Lock();
    private AtomicBoolean machineHalt = new AtomicBoolean(false);
    private int steps = 0;
    private double delay = 0;
    private long uiUpdTimestamp = System.currentTimeMillis();
    private ExecutionDelayer delayer;

    ExecuteTab(ExecuteController ec, Stage stage) {
        this.ec = ec;

        initExeTable();

        descFc = new CustomFileChooser(stage, ec.descPath, ec.descChooseFile, ec.descPathBtn, true)
                .setDialogTitle(DESC_FC_TITLE)
                .setPostAction(this::uploadMachine)
                .compile();
        descLoadVerdict = new LiveLabel(ec.descLoadVerdict);
    }

    void postInit() {
        addListeners();
        Main.fixBlurryText(ec.descPane, ec.exePane);
    }



    private void initExeTable() {
        exeTableSymPanes = new ArrayList<>();
        exeTableSyms = new ArrayList<>();
        updateExeTable(false);
        ec.exePane.hbarPolicyProperty().setValue(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        ec.exePane.vbarPolicyProperty().setValue(ScrollPane.ScrollBarPolicy.NEVER);
    }

    private void addListeners() {
        ec.choiceTypeToLoad.setOnAction(event -> {
            machineType = ec.changeMachineType();
            descFc.updateChooserInitPath();
        });

        ec.inputString.setOnKeyTyped(event -> {
            if (ec.inputHasBlanks(machine))
                ec.addExeError(ec.BLANK_ERR);
            else
                ec.removeExeError(ec.BLANK_ERR);
            resetMachine();
        });

        ec.stepDelay.setOnKeyTyped(event -> {
            if (machineBusy.isLocked()) {
                machineHalt.set(true);
            }
        });

        ec.startBtn.setOnMouseClicked(event -> {
            if (machine == null) {
                ec.addExeError(ec.NO_MACHINE_ERR);
            } else if (ec.hasNoExeErrors()) {
                delay = ec.getAndValidateDelay();
                machineHalt.set(false);
                if (machineBusy.lock()) {
                    updateMachineState(false);
                    Main.runInNewThread(() -> {
                        delayer = new ExecutionDelayer(delay);
                        while (!machineHalt.get() && !machine.isInTerminalState()) {
                            makeStep(false, true);
                            delayer.delay();
                        }
                        updateMachineState(true);
                        machineHalt.set(false);
                        machineBusy.unlock();
                    });
                }
            }
        });

        ec.stepBtn.setOnMouseClicked(event -> {
            if (machine == null) {
                ec.addExeError(ec.NO_MACHINE_ERR);
            } else if (ec.hasNoExeErrors()) {
                delay = ec.getAndValidateDelay();
                machineBusy.tryWithLock(() -> {
                    if (!machine.isInTerminalState()) {
                        makeStep(true, false);
                    }
                });
            }
        });

        ec.stopBtn.setOnMouseClicked(event -> {
            if (machineBusy.isLocked()) {
                machineHalt.set(true);
            }
        });

        ec.resetBtn.setOnMouseClicked(event -> resetMachine());
    }



    private void resetMachine() {
        if (machine != null) {
            if (machineBusy.isLocked()) {
                machineHalt.set(true);
            }
            Main.runInNewThread(() -> machineBusy.doWithLock(() -> {
                resetMachineState();
                updateMachineState(true);
            }));
        }
    }

    private void resetMachineState() {
        machine.init(ec.inputString.getText());
        steps = 0;
    }

    private void uploadMachine(Path path) {
        machineHalt.set(true);
        try {
            String content = Files.readString(path);
            ParseVerdict verdict;
            switch (machineType) {
                case DFA:
                    machine = new DFA();
                    break;
                case NFA:
                    machine = new NFA();
                    break;
                case DCA:
                    machine = new DCA();
                    break;
                case DPDA:
                    machine = new DPDA();
                    break;
                case ONEWAY:
                    machine = new OneTM();
                    break;
                case TURING:
                    machine = new TM();
                    break;
                case MULTITAPE:
                    machine = new MTM();
                    break;
                default:
                    throw new IllegalStateException("Something went wrong (invalid machine type)");
            }
            verdict = machine.parse(content);
            verdict.throwFirstError();
            ec.desc.setText(content);
            descLoadVerdict.setText("Uploaded!", 1);
            ec.setDescFileName(path.getFileName().toString());
            ec.descPane.setVisible(true);
            ec.removeExeError(ec.NO_MACHINE_ERR);
            ec.updateStatesTxt();
            resetMachineState();
            updateMachineState(false);
        } catch (IOException e) {
            descLoadVerdict.setText("Invalid machine description path:  " + path, -1);
        } catch (ParseException | IllegalStateException e) {
            descLoadVerdict.setText(e.getMessage(), -1);
            machine = null;
            ec.setDescFileName(null);
            ec.descPane.setVisible(false);
            ec.state.setText(" ");
            ec.steps.setText(" ");
        }
    }

    private void updateExeTableShape(int oldTapes, int newTapes, int oldLen, int newLen) {
        boolean smthChanges = oldTapes != newTapes || oldLen != newLen;

        if (smthChanges && oldTapes != 0) {
            ec.exeTable.getChildren().remove(2 * oldTapes * oldLen);
        }

        if (oldTapes != newTapes) {
            int cnt = exeTableSymPanes.size();
            for (int i = cnt - 1; i >= 0; i--) {
                ec.exeTable.getChildren().remove(i);
                exeTableSymPanes.remove(i);
                exeTableSyms.remove(i);
            }
            oldLen = 0;
        }

        switch (Integer.compare(oldLen, newLen)) {
            case -1:
                for (int i = oldLen; i < newLen; i++) {
                    for (int j = 0; j < newTapes; j++) {
                        for (int k = 0; k < 2; k++) {
                            Text symText = new Text();
                            FlowPane symPane = new FlowPane(symText);
                            symPane.setMinWidth(15);
                            symPane.setPrefWidth(15);
                            if (k == 0) {
                                symPane.setMinHeight(20);
                                symPane.setPrefHeight(20);
                            }
                            symPane.setAlignment(Pos.CENTER);
                            GridPane.setMargin(symPane, new Insets(SMALL_MARGIN, 0, 0, SMALL_MARGIN));
                            if (k == 1) {
                                symText.setText("^");
                                symText.setVisible(false);
                                symPane.setStyle("-fx-background-color: #ffffff");
                            }
                            exeTableSyms.add(symText);
                            exeTableSymPanes.add(symPane);
                            ec.exeTable.add(symPane, i, 2 * j + k);
                        }
                    }
                }
                break;
            case 1:
                for (int i = oldLen * 2 * newTapes - 1; i >= newLen * 2 * newTapes; i--) {
                    ec.exeTable.getChildren().remove(i);
                    exeTableSymPanes.remove(i);
                    exeTableSyms.remove(i);
                }
                break;
        }

        if (smthChanges && newTapes != 0) {
            Text stub = new Text("xD");
            stub.setVisible(false);
            stub.setStyle("-fx-background-color: #ffffff");
            ec.exeTable.add(stub, 0, 2 * newTapes);
        }
    }

    private void updateExeTable(boolean isMultithreaded) {
        Machine m = machine == null? TM.EXE_STUB : machine;
        int oldTapes = ec.exeTable.getRowCount() / 2;
        int newTapes = m.tapes();
        int oldLen = oldTapes == 0? 0 : exeTableSymPanes.size() / (2 * oldTapes);
        int newLen = Math.min(MAX_TAPE_LENGTH + 1, IntStream.range(0, newTapes).map(m::getTapeSize).max().getAsInt());

        doSceneGraphUpdate(isMultithreaded, () -> {
            updateExeTableShape(oldTapes, newTapes, oldLen, newLen);

            for (int i = 0; i < newLen; i++) {
                for (int j = 0; j < newTapes; j++) {
                    int symIndex = i * 2 * newTapes + j * 2, ptrIndex = symIndex + 1;
                    FlowPane pane = exeTableSymPanes.get(symIndex);
                    Text text = exeTableSyms.get(symIndex);
                    if (i == MAX_TAPE_LENGTH) {
                        Colors.setColor(pane, Color.WHITE);
                        text.setText(">>");
                        exeTableSyms.get(ptrIndex).setText(">>");
                        exeTableSyms.get(ptrIndex).setVisible(true);
                    } else if (i < m.getTapeSize(j)) {
                        Colors.setColor(pane, m.getTapeContentColor(j, i));
                        text.setText(i < m.getTapeSize(j)? m.getTapeContent(j, i) : m.getBlank());
                        pane.setPrefWidth(text.getLayoutBounds().getWidth() + SMALL_MARGIN);
                        exeTableSyms.get(ptrIndex).setVisible(m.getTapeContentPointer(j, i));
                    } else {
                        Colors.setColor(pane, Color.WHITE);
                        text.setText("");
                    }
                }
            }
        });
    }

    private void updateMachineState(boolean isMultithreaded) {
        Platform.runLater(() -> ec.state.setText(machine.getCurrentState()));
        if (machine.isInAcceptState())
            ec.state.setTextFill(Color.LIME);
        else if (machine.isInRejectState())
            ec.state.setTextFill(Color.RED);
        else if (machine.isInStartState())
            ec.state.setTextFill(Color.BLUEVIOLET);
        else
            ec.state.setTextFill(Color.BLACK);
        ec.steps.setText(String.format("%,d", steps));
        updateExeTable(isMultithreaded);
    }

    private void makeStep(boolean doNecessaryUpdate, boolean isMultithreaded) {
        machine.makeStep();
        steps++;
        if (doNecessaryUpdate || 1000.0 / (System.currentTimeMillis() - uiUpdTimestamp) < MAX_MACHINE_FPS) {
            updateMachineState(isMultithreaded);
            uiUpdTimestamp = System.currentTimeMillis();
        }
    }



    private void doSceneGraphUpdate(boolean isMultithreaded, Runnable update) {
        if (isMultithreaded) {
            while (true) {
                if (machineUILock.lock()) {
                    Platform.runLater(() -> {
                        update.run();
                        machineUILock.unlock();
                    });
                    break;
                }
            }
            machineUILock.waitUntilUnlocked();
        } else {
            update.run();
        }
    }



    private static class ExecutionDelayer {

        private long startTime;
        private long steps;
        private double delay;

        ExecutionDelayer(double delayMs) {
            this.delay = delayMs;
        }

        void delay() {
            if (steps == 0) {
                startTime = System.currentTimeMillis();
                try {
                    Thread.sleep((long) delay);
                } catch (InterruptedException ignored) {}
            } else {
                long current = System.currentTimeMillis();
                long scheduled = startTime + (long) (steps * delay);
                if (current < scheduled) {
                    try {
                        Thread.sleep(scheduled - current);
                    } catch (InterruptedException ignored) {}
                }
            }
            steps++;
        }

    }

}
