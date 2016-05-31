package org.csstudio.display.builder.representation.javafx.sandbox;

import org.csstudio.display.builder.representation.javafx.MarkerAxis;

import javafx.application.Application;
import javafx.beans.binding.DoubleBinding;
import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

/**
 * @author Amanda Carpenter
 */
public class SliderDemo2 extends Application
{
    public static void main(String [] args)
    {
        launch(args);
    }

    @SuppressWarnings("nls")
    @Override
    public void start(final Stage stage)
    {
        Slider slider = new Slider();
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setSnapToTicks(true);
        slider.setMinorTickCount(5);
        slider.setPrefWidth(400);

        MarkerAxis<Slider> axis = new MarkerAxis<Slider>(slider)
        {
            {
                slider.orientationProperty().addListener( (property, oldval, newval) ->
                    makeVertical(newval==Orientation.VERTICAL)
                );
            }

            @Override
            protected void initializeBindings(Slider node)
            {
                length = new DoubleBinding()
                {
                    {
                        super.bind(node.widthProperty(), node.heightProperty(), node.orientationProperty());
                    }

                    @Override
                    protected double computeValue()
                    {
                        return node.getOrientation() == Orientation.HORIZONTAL ?
                                node.getWidth() :
                                node.getHeight();
                    }
                };
                min = new DoubleBinding()
                {
                    {
                        super.bind(node.minProperty());
                    }

                    @Override
                    protected double computeValue()
                    {
                        return node.getMin();
                    }
                };
                max = new DoubleBinding()
                {
                    {
                        super.bind(node.maxProperty());
                    }

                    @Override
                    protected double computeValue()
                    {
                        return node.getMax();
                    }
                };
            }
        };

        Button button = new Button("Rotate me.");
        button.setOnAction((event)->
            {
                slider.setOrientation(slider.getOrientation()==Orientation.HORIZONTAL ?
                        Orientation.VERTICAL : Orientation.HORIZONTAL);
            }
        );
        Button button2 = new Button("Toggle hihi");
        button2.setOnAction((event)->
        {
            axis.setShowHiHi(!axis.getShowHiHi());
        }
        );
        Button button3 = new Button("Adjust lo");
        button3.setOnAction((event)->
        {
            if ("Adjust length".equals(button3.getText()))
            {   //adjusting length
                button3.setText("Adjust lo");
                slider.setValue(slider.getOrientation()==Orientation.HORIZONTAL ?
                        slider.getPrefWidth()-400 : slider.getPrefHeight()-400);
            }
            else
            {   //adjusting lo
                button3.setText("Adjust length");
                slider.setValue(axis.getLo());
            }
        });

        HBox buttons = new HBox(10.0, new Text("[Slide to adjust.]"), button3, button, button2);

        final GridPane pane = new GridPane();
        pane.setAlignment(Pos.CENTER);
        GridPane.setVgrow(slider, Priority.NEVER);
        GridPane.setHgrow(slider, Priority.NEVER);
        GridPane.setConstraints(slider, 0, 1, 1, 1, HPos.CENTER, VPos.CENTER);
        pane.getChildren().add(slider);

        final VBox root = new VBox(pane, buttons);

        reorient(slider.getOrientation(), pane, axis);
        slider.orientationProperty().addListener((property, oldval, newval)->
        {
            reorient(newval, pane, axis);
        });
        slider.valueProperty().addListener((observable, oldval, newval)->
        {
            if ("Adjust lo".equals(button3.getText()))
            {
                if (slider.getOrientation()==Orientation.HORIZONTAL)
                {
                    slider.setPrefWidth(400+newval.doubleValue());
                    slider.setMaxWidth(400+newval.doubleValue());
                }
                else
                {
                    slider.setPrefHeight(400+newval.doubleValue());
                    slider.setMaxHeight(400+newval.doubleValue());
                }
            }
            else
                axis.setLo(newval.doubleValue());
        });

        final Scene scene = new Scene(root, 800, 700);
        stage.setScene(scene);
        stage.setTitle("Slider Demo");

        stage.show();
    }

    private void reorient(Orientation newValue, GridPane pane, MarkerAxis<Slider> axis)
    {
        boolean showMarkers = true;
        Node slider = pane.getChildren().get(0);
        if (showMarkers)
        {
            if (newValue == Orientation.HORIZONTAL)
                GridPane.setConstraints(slider, 0, 1, 1, 1, HPos.CENTER, VPos.CENTER);
            else
                GridPane.setConstraints(slider, 1, 0, 1, 1, HPos.CENTER, VPos.CENTER);
            if (!pane.getChildren().contains(axis))
                pane.add(axis, 0, 0);
        }
        else
        {
            pane.getChildren().removeIf((child)->child instanceof MarkerAxis);
            GridPane.setConstraints(slider, 0, 0);
        }
    }
}