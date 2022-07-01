import misc.LiveLabel;
import misc.TabPaneConstructor;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class Main extends Application {

    private static final String TITLE = "Turing Machine Executor";

    public void start(Stage stage) throws Exception {

        FXMLLoader exeLoader = new FXMLLoader();
        Parent execute = exeLoader.load(getClass().getResource("execute.fxml").openStream());
        ExecuteTab exeTab = new ExecuteTab(exeLoader.getController(), stage);

        FXMLLoader editLoader = new FXMLLoader();
        Parent edit = editLoader.load(getClass().getResource("edit.fxml").openStream());
        EditTab editTab = new EditTab(editLoader.getController(), stage);

        FXMLLoader convertLoader = new FXMLLoader();
        Parent convert = convertLoader.load(getClass().getResource("convert.fxml").openStream());
        ConvertTab convertTab = new ConvertTab(convertLoader.getController());

        Parent main = new TabPaneConstructor(3)
                .set(0, "Execute", 80, execute)
                .set(1, "Edit", 54, edit)
                .set(2, "Convert", 80, convert)
                .compile();

        stage.setTitle(TITLE);
        stage.setScene(new Scene(main, 960, 720));
        stage.setMinWidth(600);
        stage.setMinHeight(480);
        stage.show();

        exeTab.postInit();
        editTab.postInit();
        convertTab.postInit();

        LiveLabel.runRegistry();
    }

    static void runInNewThread(Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.start();
    }

    static void fixBlurryText(ScrollPane... scrollPanes) {
        runInNewThread(() -> {
            boolean[] success = new boolean[scrollPanes.length];
            boolean allSuccesses = false;
            while (!allSuccesses) {
                for (int i = 0; i < scrollPanes.length; i++) {
                    if (!success[i]) {
                        if (scrollPanes[i] == null)
                            continue;
                        StackPane stackPane = (StackPane) scrollPanes[i].lookup("ScrollPane .viewport");
                        if (stackPane == null)
                            continue;
                        stackPane.setCache(false);
                        success[i] = true;
                    }
                }

                allSuccesses = true;
                for (boolean s : success)
                    allSuccesses &= s;
            }
        });
    }

}