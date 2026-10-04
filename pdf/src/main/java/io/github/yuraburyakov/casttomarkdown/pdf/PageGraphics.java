package io.github.yuraburyakov.casttomarkdown.pdf;

import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.util.Matrix;

/**
 * The boxes of what a page paints: filled or stroked paths and images, form XObjects included; text is not
 * included. Coordinates as {@link Line#pageX()} and {@link Line#pageY()}: from the left and the top of the
 * crop box. A curve is measured by its control points, which enclose it.
 */
final class PageGraphics extends PDFGraphicsStreamEngine {

    /** A box narrower or lower than this many points is a thin line: a table rule, an underline. */
    private static final float THIN = 1.5f;
    /**
     * Pages that paint more boxes are skipped by figure and table detection: joining boxes into drawings is
     * quadratic, and an untrusted PDF can paint millions of tiny boxes in a few kilobytes. Only one box more
     * is kept, so that memory stays small too. arXiv pages paint at most 419.
     */
    static final int MAX_BOXES = 10_000;

    /** A painted area; {@code top} is above {@code bottom}, so {@code top < bottom}. */
    record Box(float left, float top, float right, float bottom) {

        boolean thin() {
            return right - left < THIN || bottom - top < THIN;
        }

        boolean contains(float x, float y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }

        /** Whether the boxes overlap or are at most {@code distance} apart. */
        boolean near(Box other, float distance) {
            return left - distance <= other.right && other.left - distance <= right
                    && top - distance <= other.bottom && other.top - distance <= bottom;
        }

        Box union(Box other) {
            return new Box(Math.min(left, other.left), Math.min(top, other.top),
                    Math.max(right, other.right), Math.max(bottom, other.bottom));
        }

        Box grow(float distance) {
            return new Box(left - distance, top - distance, right + distance, bottom + distance);
        }
    }

    private final float originX;
    private final float pageTop;
    private final List<Box> boxes = new ArrayList<>();
    /** The path being built, {@code null} when there is none. */
    private Box path;
    /** Whether the path being built becomes the clip when it ends ({@code W n}). */
    private boolean clipping;
    private final Point2D.Float current = new Point2D.Float();

    private PageGraphics(PDPage page) {
        super(page);
        PDRectangle crop = page.getCropBox();
        originX = crop.getLowerLeftX();
        pageTop = crop.getUpperRightY();
    }

    /** The painted boxes of {@code page}, in content stream order; at most {@link #MAX_BOXES} + 1. */
    static List<Box> of(PDPage page) throws IOException {
        PageGraphics graphics = new PageGraphics(page);
        graphics.processPage(page);
        return graphics.boxes;
    }

    private void add(double x, double y) {
        float left = (float) x - originX;
        float top = pageTop - (float) y;
        Box point = new Box(left, top, left, top);
        path = path == null ? point : path.union(point);
    }

    private void paint() {
        if (path != null && boxes.size() <= MAX_BOXES) {
            Box visible = visible(path);
            if (visible != null) {
                boxes.add(visible);
            }
        }
        applyClip();
        path = null;
    }

    /**
     * The part of the box inside the clipping path, {@code null} when none of it is: a placed picture's
     * background can reach far beyond what is shown, under the text of the next column.
     */
    private Box visible(Box box) {
        Rectangle2D clip = getGraphicsState().getCurrentClippingPath().getBounds2D();
        float left = Math.max(box.left(), (float) clip.getMinX() - originX);
        float right = Math.min(box.right(), (float) clip.getMaxX() - originX);
        float top = Math.max(box.top(), pageTop - (float) clip.getMaxY());
        float bottom = Math.min(box.bottom(), pageTop - (float) clip.getMinY());
        return left <= right && top <= bottom ? new Box(left, top, right, bottom) : null;
    }

    @Override
    public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
        add(p0.getX(), p0.getY());
        add(p1.getX(), p1.getY());
        add(p2.getX(), p2.getY());
        add(p3.getX(), p3.getY());
        current.setLocation(p0);
    }

    @Override
    public void drawImage(PDImage image) {
        Matrix matrix = getGraphicsState().getCurrentTransformationMatrix();
        Box before = path;
        path = null;
        for (float[] corner : new float[][] {{0, 0}, {1, 0}, {0, 1}, {1, 1}}) {
            Point2D.Float point = matrix.transformPoint(corner[0], corner[1]);
            add(point.x, point.y);
        }
        paint();
        path = before;
    }

    @Override
    public void clip(int windingRule) {
        // the clip takes effect when the path ends, as in PDFBox's own renderer
        clipping = true;
    }

    @Override
    public void moveTo(float x, float y) {
        add(x, y);
        current.setLocation(x, y);
    }

    @Override
    public void lineTo(float x, float y) {
        add(x, y);
        current.setLocation(x, y);
    }

    @Override
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        add(x1, y1);
        add(x2, y2);
        add(x3, y3);
        current.setLocation(x3, y3);
    }

    @Override
    public Point2D getCurrentPoint() {
        return current;
    }

    @Override
    public void closePath() {
    }

    @Override
    public void endPath() {
        applyClip();
        path = null;
    }

    /** Makes the ended path the clip when {@code W} asked for it; it takes effect after painting. */
    private void applyClip() {
        if (clipping && path != null) {
            // ponytail: clipped to the bounding box of the clip path, not its exact shape; placed pictures
            // and figure panels are clipped by rectangles
            getGraphicsState().intersectClippingPath(new Area(new Rectangle2D.Float(path.left() + originX,
                    pageTop - path.bottom(), path.right() - path.left(), path.bottom() - path.top())));
        }
        clipping = false;
    }

    @Override
    public void strokePath() {
        paint();
    }

    @Override
    public void fillPath(int windingRule) {
        paint();
    }

    @Override
    public void fillAndStrokePath(int windingRule) {
        paint();
    }

    @Override
    public void shadingFill(COSName shadingName) {
        // ponytail: a shading fill paints the clip area, which is not tracked; charts in the corpus use paths
    }
}
