package carehome.ui;


// small helper class.
import carehome.model.Role;
import carehome.model.Staff;
import carehome.service.CareHome;
import carehome.ui.controller.LoginController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.time.LocalDate;

public class MainApp extends Application {

    private CareHome careHome;

    @Override
    public void start(Stage primaryStage) throws Exception {
        careHome = new CareHome();
        if (!careHome.hasAnyBeds()) careHome.seedDefaultLayout();

        // seed minimal accounts so we can log in
        careHome.addOrUpdateStaff("M1", new Staff("M1","Manager", Role.MANAGER), "manager","pass");
        careHome.addOrUpdateStaff("M1", new Staff("D1","Dr Alice", Role.DOCTOR), "alice","pass");
        careHome.addOrUpdateStaff("M1", new Staff("N1","Nurse Bob", Role.NURSE), "bob","pass");

        // optional shifts for today (so doctor/nurse are rostered)
        var today = LocalDate.now();
        careHome.allocateShift("M1", new carehome.model.Shift("N1", today.atTime(8,0),  today.atTime(16,0)));

        FXMLLoader loader = new FXMLLoader(getClass().getResource("LoginView.fxml"));
        Scene scene = new Scene(loader.load());
        LoginController lc = loader.getController();
        lc.setCareHome(careHome);

        primaryStage.setTitle("RMIT Care Home — Login");
        primaryStage.setScene(scene);
        primaryStage.setWidth(520);
        primaryStage.setHeight(320);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
