import machines.Machine;
import machines.convert.Convert;
import machines.parser.ParseVerdict;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;

class ConvertTab {

    private ConvertController cc;

    ConvertTab(ConvertController cc) {
        this.cc = cc;
    }

    void postInit() {
        cc.btn.setOnMouseClicked(event -> {
            try {
                Path from = Path.of(cc.from.getText());
                String content = Files.readString(from);
                String code = cc.code.getText();
                Machine m = Convert.init(code);
                ParseVerdict parseVerdict = m.parse(content);
                parseVerdict.throwFirstError();
                Machine converted = Convert.convert(m, code);
                Path to = Path.of(cc.to.getText());
                Files.writeString(to, converted.toString());
            } catch (IOException | ParseException e) {
                e.printStackTrace();
            }
        });
    }

}
