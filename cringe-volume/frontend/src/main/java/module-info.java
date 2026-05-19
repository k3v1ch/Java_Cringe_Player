module com.cringe.player {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires java.net.http;
    requires com.google.gson;

    opens com.cringe.player.ui to javafx.fxml;
    opens com.cringe.player.payment to javafx.fxml;

    exports com.cringe.player;
    exports com.cringe.player.api;
    exports com.cringe.player.player;
    exports com.cringe.player.payment;
    exports com.cringe.player.ui;
}
