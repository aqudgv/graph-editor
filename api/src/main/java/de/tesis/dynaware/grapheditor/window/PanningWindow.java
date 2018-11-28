/*
 * Copyright (C) 2005 - 2014 by TESIS DYNAware GmbH
 */
package de.tesis.dynaware.grapheditor.window;

import de.tesis.dynaware.grapheditor.utils.GraphEditorProperties;
import de.tesis.dynaware.grapheditor.utils.GraphEventManager;
import de.tesis.dynaware.grapheditor.utils.GraphInputGesture;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;


/**
 * A window over a large {@link Region} of content.
 *
 * <p>
 * This window can be panned around relative to its content. Only the parts of
 * the content that are inside the window will be rendered. Everything outside
 * it is clipped.
 * </p>
 */
public class PanningWindow extends Region {

    private static final float SCALE_MIN = 0.5f;
    private static final float SCALE_MAX = 1.5f;

    private final Rectangle clip = new Rectangle();

    private Region content;

    private final DoubleProperty contentX = new SimpleDoubleProperty()
    {

        @Override
        protected void invalidated()
        {
            requestLayout();
        }
    };
    private final DoubleProperty contentY = new SimpleDoubleProperty()
    {

        @Override
        protected void invalidated()
        {
            requestLayout();
        }
    };
    private final ScrollBar scrollX = new ScrollBar();
    private final ScrollBar scrollY = new ScrollBar();

    private final EventHandler<MouseEvent> mousePressedHandler = this::handlePanningMousePressed;
    private final EventHandler<MouseEvent> mouseDraggedHandler = this::handlePanningMouseDragged;
    private final EventHandler<MouseEvent> mouseReleasedHandler = this::handlePanningMouseReleased;

    private Point2D clickPosition;
    private Point2D windowPosAtClick;

    private final DoubleProperty zoom = new SimpleDoubleProperty(1);
    private final Scale scale = new Scale();

    private final EventHandler<ZoomEvent> zoomHandler = this::handleZoom;
    private final EventHandler<ScrollEvent> scrollHandler = this::handleScroll;

    private GraphEventManager eventManager;

    /**
     * Creates a new {@link PanningWindow}.
     */
    public PanningWindow()
    {
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        scale.xProperty().bind(zoom);
        scale.yProperty().bind(zoom);

        getChildren().addAll(scrollX, scrollY);

        scrollX.setOrientation(Orientation.HORIZONTAL);
        scrollX.valueProperty().bindBidirectional(contentX);
        scrollX.getStyleClass().add("graph-editor-scroll-bar"); //$NON-NLS-1$

        scrollY.valueProperty().bindBidirectional(contentY);
        scrollY.setOrientation(Orientation.VERTICAL);
        scrollY.getStyleClass().add("graph-editor-scroll-bar"); //$NON-NLS-1$
    }

    /**
     * Sets the editor properties object that the drag logic should respect.
     *
     * <p>
     * This method is called by the framework. Custom skins should <b>not</b>
     * call it. Editor properties should instead be set via the graph editor
     * instance.
     * </p>
     *
     * @param pEditorProperties
     *            the {@link GraphEditorProperties} instance for the graph
     *            editor
     */
    public void setEditorProperties(final GraphEditorProperties pEditorProperties)
    {
        eventManager = pEditorProperties == null ? null : pEditorProperties.getGraphEventManager();
    }

    /**
     * Pans the window to the specified x and y coordinates.
     *
     * <p>
     * The window cannot be panned outside the content. When the window 'hits
     * the edge' of the content it will stop.
     * </p>
     *
     * @param x
     *            the x position of the window relative to the top-left corner
     *            of the content
     * @param y
     *            the y position of the window relative to the top-left corner
     *            of the content
     */
    public void panTo(final double x, final double y)
    {
        final double newX = checkContentX(x);
        final double newY = checkContentY(y);
        if (newX != getContentX() || newY != getContentY())
        {
            contentX.set(newX);
            contentY.set(newY);
        }
    }

    /**
     * Pans the window to the specified x coordinate.
     *
     * <p>
     * The window cannot be panned outside the content. When the window 'hits
     * the edge' of the content it will stop.
     * </p>
     *
     * @param x
     *            the x position of the window relative to the top-left corner
     *            of the content
     */
    public void panToX(final double x)
    {
        final double newX = checkContentX(x);
        if (newX != getContentX())
        {
            contentX.set(newX);
        }
    }

    /**
     * Pans the window to the specified x coordinate.
     *
     * <p>
     * The window cannot be panned outside the content. When the window 'hits
     * the edge' of the content it will stop.
     * </p>
     *
     * @param y
     *            the y position of the window relative to the top-left corner
     *            of the content
     */
    public void panToY(final double y)
    {
        final double newY = checkContentY(y);
        if (newY != contentY.get())
        {
            contentY.set(newY);
        }
    }

    /**
     * @return the x coordinate of the window relative to the top-left corner of
     *         the content.
     */
    public double getContentX()
    {
        return contentX.get();
    }

    /**
     * @return the y coordinate of the window relative to the top-left corner of
     *         the content.
     */
    public double getContentY()
    {
        return contentY.get();
    }

    /**
     * @return {@link DoubleProperty} with the current zoom level
     * @since 14.11.2018
     */
    public DoubleProperty zoomProperty()
    {
        return zoom;
    }

    /**
     * Set new zoom level without moving the viewport
     *
     * @param pZoom
     *            new zoom factor
     */
    public void setZoom(final double pZoom)
    {
        setZoomAt(pZoom, getContentX(), getContentY());
    }

    /**
     * Zoom at the given location
     *
     * @param pZoom
     *            new zoom factor
     * @param pPivotX
     *            the X coordinate about which point the scale occurs
     * @param pPivotY
     *            the Y coordinate about which point the scale occurs
     */
    public void setZoomAt(final double pZoom, final double pPivotX, final double pPivotY)
    {
        final double oldZoomLevel = getZoom();
        final float newZoomLevel = constrainZoom((float) pZoom);

        if (newZoomLevel != oldZoomLevel)
        {
            final double f = newZoomLevel / oldZoomLevel - 1;
            zoom.set(newZoomLevel);
            panTo(getContentX() + f * pPivotX, getContentY() + f * pPivotY);
        }
    }

    /**
     * @return current zoom factor
     */
    public double getZoom()
    {
        return zoom.get();
    }

    @Override
    protected void layoutChildren()
    {
        super.layoutChildren();
        final double height = getHeight();
        final double width = getWidth();

        // content
        content.relocate(-contentX.get(), -contentY.get());

        // scrollbars
        final double w = scrollY.getWidth();
        final double h = scrollX.getHeight();

        scrollX.resizeRelocate(0, snapPosition(height - h), snapSize(width - w), h);
        scrollY.resizeRelocate(snapPosition(width - w), 0, w, snapSize(height - h));

        scrollX.setMin(0);
        scrollX.setMax(getMaxX());
        scrollX.setVisibleAmount(width);
        scrollY.setMin(0);
        scrollY.setMax(getMaxY());
        scrollY.setVisibleAmount(height);
    }

    private static float constrainZoom(final float zoom)
    {
        final float a = zoom >= SCALE_MIN ? zoom : SCALE_MIN;
        return a <= SCALE_MAX ? a : SCALE_MAX;
    }

    /**
     * Checks that the window bounds are completely inside the content bounds,
     * and repositions if necessary.
     *
     * <p>
     * Also checks that the window position values are integers to ensure
     * strokes are drawn cleanly.
     * </p>
     */
    protected void checkWindowBounds()
    {
        panTo(getContentX(), getContentY());
    }

    private double getMaxX()
    {
        return content != null ? content.getBoundsInParent().getWidth() - getWidth() : 0;
    }

    private double getMaxY()
    {
        return content != null ? content.getBoundsInParent().getHeight() - getHeight() : 0;
    }

    private double checkContentX(final double xToCheck)
    {
        return snapPosition(Math.min(getMaxX(), Math.max(xToCheck, 0)));
    }

    private double checkContentY(final double yToCheck)
    {
        return snapPosition(Math.min(getMaxY(), Math.max(yToCheck, 0)));
    }

    @Override
    public ObservableList<Node> getChildren()
    {
        return super.getChildren();
    }

    /**
     * Sets the content of the panning window.
     *
     * <p>
     * Note that the content's {@code managed} attribute will be set to false.
     * Its size must therefore be set manually using the {@code resize()} method
     * of the {@link Node} class.
     * </p>
     *
     * @param pContent
     *            the {@link Region} to be displayed inside the panning window
     */
    protected void setContent(final Region pContent)
    {
        // Remove children and release bindings from old content, if any exists.
        final Region prevContent = content;
        if (prevContent != null)
        {
            removeMouseHandlersFromContent(prevContent);
            getChildren().remove(prevContent);
            prevContent.getTransforms().remove(scale);
        }

        content = pContent;

        if (pContent != null)
        {
            pContent.setManaged(false);
            getChildren().add(pContent);
            addMouseHandlersToContent(pContent);
            pContent.getTransforms().add(scale);

            scrollX.setVisible(true);
            scrollY.setVisible(true);
        }
        else
        {
            scrollX.setVisible(false);
            scrollY.setVisible(false);
        }
    }

    private boolean canPan(final MouseEvent event)
    {
        return eventManager != null && eventManager.canActivate(GraphInputGesture.PAN, event);
    }

    private void handlePanningMousePressed(final MouseEvent event)
    {
        if (canPan(event))
        {
            startPanning(event.getScreenX(), event.getScreenY());
        }
    }

    private void handlePanningMouseDragged(final MouseEvent event)
    {
        if (!canPan(event))
        {
            return;
        }

        if (eventManager.getInputGesture() == null)
        {
            startPanning(event.getScreenX(), event.getScreenY());
        }

        final double deltaX = event.getScreenX() - clickPosition.getX();
        final double deltaY = event.getScreenY() - clickPosition.getY();

        final double newWindowX = windowPosAtClick.getX() - deltaX;
        final double newWindowY = windowPosAtClick.getY() - deltaY;

        panTo(newWindowX, newWindowY);
    }

    private void handlePanningMouseReleased(final MouseEvent event)
    {
        if (eventManager != null && eventManager.finishInputGesture(GraphInputGesture.PAN))
        {
            setCursor(null);
            event.consume();
        }
    }

    private void handleScroll(final ScrollEvent pEvent)
    {
        // this intended for mouse-scroll events (event direct == false)
        // the event also gets synthesized from touch events, which we want to ignore as they are handled in handleZoom()
        if (pEvent.isDirect() || pEvent.getTouchCount() > 0 || eventManager == null)
        {
            return;
        }

        pEvent.consume();

        if (pEvent.isControlDown() && eventManager.canActivate(GraphInputGesture.ZOOM, pEvent))
        {
            final double modifier = pEvent.getDeltaY() > 1 ? 0.06 : -0.06;
            final double newZoomLevel = getZoom() + modifier;
            setZoomAt(newZoomLevel, pEvent.getX(), pEvent.getY());
        }
        else if (!pEvent.isControlDown() && eventManager.canActivate(GraphInputGesture.PAN, pEvent))
        {
            panTo(getContentX() - pEvent.getDeltaX(), getContentY() - pEvent.getDeltaY());
        }
    }

    private void handleZoom(final ZoomEvent pEvent)
    {
        if (eventManager == null || !eventManager.canActivate(GraphInputGesture.ZOOM, pEvent))
        {
            return;
        }

        if (pEvent.getEventType() == ZoomEvent.ZOOM_STARTED)
        {
            eventManager.activateInputGesture(GraphInputGesture.ZOOM);
        }
        else if (pEvent.getEventType() == ZoomEvent.ZOOM_FINISHED)
        {
            eventManager.finishInputGesture(GraphInputGesture.ZOOM);
        }
        else
        {
            final double newZoomLevel = getZoom() * pEvent.getZoomFactor();
            setZoomAt(newZoomLevel, pEvent.getX(), pEvent.getY());
        }
        pEvent.consume();
    }

    /**
     * Adds handlers to the content for panning and zooming.
     */
    private void addMouseHandlersToContent(final Node pContent)
    {
        pContent.addEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
        pContent.addEventHandler(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);

        pContent.addEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleasedHandler);
        // sometimes MOUSE_RELEASED is not delivered but the MOUSE_CLICKED..
        pContent.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseReleasedHandler);

        pContent.addEventHandler(ZoomEvent.ANY, zoomHandler);
        pContent.addEventHandler(ScrollEvent.SCROLL, scrollHandler);
    }

    /**
     * Removes existing handlers from the content, if possible.
     */
    private void removeMouseHandlersFromContent(final Node pContent)
    {
        pContent.removeEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
        pContent.removeEventHandler(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);

        pContent.removeEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleasedHandler);
        // sometimes MOUSE_RELEASED is not delivered but the MOUSE_CLICKED..
        pContent.removeEventHandler(MouseEvent.MOUSE_CLICKED, mouseReleasedHandler);

        pContent.removeEventHandler(ZoomEvent.ANY, zoomHandler);
        pContent.removeEventHandler(ScrollEvent.SCROLL, scrollHandler);
    }

    /**
     * Starts panning. Should be called on mouse-pressed or when a drag event
     * occurs without a pressed event having been registered. This can happen if
     * e.g. a context menu closes and consumes the pressed event.
     *
     * @param x
     *            the scene-x position of the cursor
     * @param y
     *            the scene-y position of the cursor
     */
    private void startPanning(final double x, final double y)
    {
        eventManager.activateInputGesture(GraphInputGesture.PAN);
        setCursor(Cursor.MOVE);

        clickPosition = new Point2D(x, y);
        windowPosAtClick = new Point2D(getContentX(), getContentY());
    }
}