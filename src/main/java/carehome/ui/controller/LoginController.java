package carehome.ui.controller;


// controller for the Login screen.
import carehome.model.Staff;
import carehome.service.CareHome;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.application.Platform;


/**
 * Handles the login screen (ID + password).
 * Expects a shared CareHome instance to be provided via setCareHome
 */
public class LoginController {

    @FXML private TextField txtId;
    @FXML private PasswordField txtPassword;
    @FXML private Button btnLogin;
    @FXML private Label lblError;

    private CareHome careHome;   // injected by MainApp

    /** Inject the shared service instance from MainApp */
    public void setCareHome(CareHome ch) {
        this.careHome = ch;
    }

    @FXML
    public void initialize() {
        // Click or press Enter to login
        btnLogin.setOnAction(e -> doLogin());
        txtPassword.setOnAction(e -> doLogin());
        txtId.setOnAction(e -> doLogin());
    }

    private void doLogin() {
        lblError.setText("");

        try {
            if (careHome == null) {
                throw new IllegalStateException("System not initialized (CareHome is null).");
            }

            String id = txtId.getText() == null ? "" : txtId.getText().trim();
            String pw = txtPassword.getText() == null ? "" : txtPassword.getText();

            if (id.isEmpty() || pw.isEmpty()) {
                lblError.setText("Please enter both ID and password.");
                return;
            }

            // Authenticate
            Staff current = careHome.authenticate(id, pw);

            // Load main window
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/carehome/ui/MainView.fxml"));
            Scene scene = new Scene(loader.load());

            // Pass context to the main controller
            MainController mc = loader.getController();
            mc.setContext(careHome, current);

            // Swap scene on the same stage
            Stage stage = (Stage) btnLogin.getScene().getWindow();
            stage.setTitle("RMIT Care Home Management — " + current.getName() + " (" + current.getRole() + ")");
            stage.setScene(scene);
            stage.show();

            //  Show success popup after main window is visible
            Platform.runLater(() ->
                    mc.showInfo("Login Successful",
                            "Welcome, " + current.getName() + " (" + current.getRole() + ")")
            );

        } catch (Exception ex) {
            lblError.setText("Login failed: " + ex.getMessage());
        }
    }
}
