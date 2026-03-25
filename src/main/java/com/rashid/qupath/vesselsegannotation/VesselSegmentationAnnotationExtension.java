package com.rashid.qupath.vesselsegannotation;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.RegionRequest;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;

import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;

import javax.imageio.ImageIO;

public class VesselSegmentationAnnotationExtension implements QuPathExtension {

    private static final String PYTHON_EXEC =
            "/Users/rashid/Installations/venve/bin/python3";

    private static final String PYTHON_SCRIPT =
            "/Users/rashid/Installations/extensions/vessel_segmentation_v003.py";

    @Override
    public void installExtension(QuPathGUI qupath) {
        var menu = qupath.getMenu("Extensions > Vessel Segmentation Annotation", true);

        MenuItem item = new MenuItem("Run Vessel Segmentation");
        item.setOnAction(e -> openWindow(qupath));

        menu.getItems().add(item);
    }

    private void openWindow(QuPathGUI qupath) {

        Stage stage = new Stage();
        stage.setTitle("Vessel Segmentation");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        Label outputLabel = new Label("Output folder:");
        TextField outputField = new TextField();
        outputField.setPrefWidth(260);

        Button browseButton = new Button("Browse");
        browseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Output Folder");
            File dir = chooser.showDialog(stage);
            if (dir != null) {
                outputField.setText(dir.getAbsolutePath());
            }
        });

        Label inputModeLabel = new Label("Input region:");

        ToggleGroup inputModeGroup = new ToggleGroup();

        RadioButton wholeImageButton = new RadioButton("Whole image");
        wholeImageButton.setToggleGroup(inputModeGroup);
        wholeImageButton.setSelected(true);

        RadioButton selectedAnnotationButton = new RadioButton("Selected annotation");
        selectedAnnotationButton.setToggleGroup(inputModeGroup);

        Label widthLabel = new Label("Kernel width:");
        TextField widthField = new TextField("21");

        Label heightLabel = new Label("Kernel height:");
        TextField heightField = new TextField("21");

        Label shapeLabel = new Label("Kernel shape:");
        ComboBox<String> shapeBox = new ComboBox<>(
                FXCollections.observableArrayList("ELLIPSE", "RECT", "CROSS")
        );
        shapeBox.setValue("ELLIPSE");

        Button runButton = new Button("Run");

        runButton.setOnAction(e -> {

            String outputPath = outputField.getText().trim();

            int kernelWidth;
            int kernelHeight;

            try {
                kernelWidth = Integer.parseInt(widthField.getText().trim());
                kernelHeight = Integer.parseInt(heightField.getText().trim());
            } catch (NumberFormatException ex) {
                showMessage(
                        Alert.AlertType.ERROR,
                        "Input Error",
                        "Kernel width and height must be integers."
                );
                return;
            }

            if (kernelWidth <= 0 || kernelHeight <= 0) {
                showMessage(
                        Alert.AlertType.ERROR,
                        "Input Error",
                        "Kernel width and height must be greater than zero."
                );
                return;
            }

            String kernelShape = shapeBox.getValue();
            boolean useSelectedAnnotation = selectedAnnotationButton.isSelected();

            runSegmentation(qupath, outputPath, kernelWidth, kernelHeight, kernelShape, useSelectedAnnotation);
        });

        grid.add(outputLabel, 0, 0);
        grid.add(outputField, 1, 0);
        grid.add(browseButton, 2, 0);

        grid.add(inputModeLabel, 0, 1);
        grid.add(wholeImageButton, 1, 1);
        grid.add(selectedAnnotationButton, 1, 2);

        grid.add(widthLabel, 0, 3);
        grid.add(widthField, 1, 3);

        grid.add(heightLabel, 0, 4);
        grid.add(heightField, 1, 4);

        grid.add(shapeLabel, 0, 5);
        grid.add(shapeBox, 1, 5);

        grid.add(runButton, 1, 6);

        Scene scene = new Scene(grid, 520, 300);
        stage.setScene(scene);
        stage.show();
    }

    private void runSegmentation(QuPathGUI qupath,
                                 String outputPath,
                                 int kernelWidth,
                                 int kernelHeight,
                                 String kernelShape,
                                 boolean useSelectedAnnotation) {

        try {
            if (qupath.getImageData() == null) {
                showMessage(
                        Alert.AlertType.ERROR,
                        "No Image",
                        "No image is currently open in QuPath."
                );
                return;
            }

            if (outputPath == null || outputPath.isBlank()) {
                showMessage(
                        Alert.AlertType.ERROR,
                        "Missing Output Folder",
                        "Please select an output folder."
                );
                return;
            }

            File outputDir = new File(outputPath);
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                showMessage(
                        Alert.AlertType.ERROR,
                        "Output Error",
                        "Could not create output folder:\n" + outputPath
                );
                return;
            }

            ImageServer<BufferedImage> server = qupath.getImageData().getServer();
            BufferedImage img;

            if (useSelectedAnnotation) {
                PathObject selectedObject = qupath.getViewer().getSelectedObject();

                if (selectedObject == null) {
                    showMessage(
                            Alert.AlertType.ERROR,
                            "No Annotation Selected",
                            "Please select an annotation in QuPath first."
                    );
                    return;
                }

                if (!selectedObject.isAnnotation()) {
                    showMessage(
                            Alert.AlertType.ERROR,
                            "Invalid Selection",
                            "The selected object is not an annotation."
                    );
                    return;
                }

                ROI roi = selectedObject.getROI();

                if (roi == null) {
                    showMessage(
                            Alert.AlertType.ERROR,
                            "Invalid ROI",
                            "The selected annotation does not contain a valid ROI."
                    );
                    return;
                }

                RegionRequest request = RegionRequest.createInstance(
                        server.getPath(),
                        1.0,
                        roi
                );

                img = server.readRegion(request);

            } else {
                img = server.readRegion(
                        RegionRequest.createInstance(
                                server.getPath(),
                                1.0,
                                0,
                                0,
                                server.getWidth(),
                                server.getHeight()
                        )
                );
            }

            File tempInputDir = Files.createTempDirectory("qupath_vessel_input").toFile();
            File tempImage = new File(tempInputDir, "qupath_export.png");

            ImageIO.write(img, "PNG", tempImage);

            ProcessBuilder pb = new ProcessBuilder(
                    PYTHON_EXEC,
                    PYTHON_SCRIPT,
                    tempInputDir.getAbsolutePath(),
                    outputPath,
                    "--dilation-kernel-width", String.valueOf(kernelWidth),
                    "--dilation-kernel-height", String.valueOf(kernelHeight),
                    "--dilation-kernel-shape", kernelShape
            );

            pb.redirectErrorStream(true);

            Process process = pb.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            StringBuilder log = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                log.append(line).append("\n");
            }

            int exitCode = process.waitFor();

            if (exitCode == 0 && log.toString().contains("VESSEL_SEGMENTATION_SUCCESS")) {
                showMessage(
                        Alert.AlertType.INFORMATION,
                        "Segmentation Finished",
                        "Vessel segmentation completed successfully.\n\nResults saved to:\n" + outputPath
                );
            } else {
                showExpandableMessage(
                        Alert.AlertType.ERROR,
                        "Segmentation Failed",
                        log.toString().isBlank() ? "Python process failed." : log.toString()
                );
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            showMessage(
                    Alert.AlertType.ERROR,
                    "Execution Error",
                    "Could not run Python segmentation:\n" + ex.getMessage()
            );
        }
    }

    private void showMessage(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showExpandableMessage(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);

        TextArea area = new TextArea(message);
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefWidth(700);
        area.setPrefHeight(350);

        alert.getDialogPane().setContent(area);
        alert.setResizable(true);
        alert.showAndWait();
    }

    @Override
    public String getName() {
        return "Vessel Segmentation Annotation";
    }

    @Override
    public String getDescription() {
        return "Vessel segmentation using current QuPath image or selected annotation.";
    }
}