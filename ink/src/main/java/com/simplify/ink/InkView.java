/*
 * Copyright (c) 2016 Mastercard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.simplify.ink;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class InkView extends View {

    /**
     * The default maximum stroke width (dp).
     * Will be used as the standard stroke width if FLAG_RESPONSIVE_WIDTH is removed
     */
    public static final float DEFAULT_MAX_STROKE_WIDTH = 5f;

    /**
     * The default minimum stroke width (dp)
     */
    public static final float DEFAULT_MIN_STROKE_WIDTH = 1.5f;

    /**
     * The default smoothing ratio for calculating the control points for the bezier curves.
     * Will be ignored if FLAG_INTERPOLATION is removed
     */
    public static final float DEFAULT_SMOOTHING_RATIO = 0.75f;

    /**
     * When this flag is added, paths will be drawn as cubic-bezier curves
     */
    public static final int FLAG_INTERPOLATION = 1;

    /**
     * When present, the width of the paths will be responsive to the velocity of the stroke.
     * When missing, the width of the path will be the the max stroke width
     */
    public static final int FLAG_RESPONSIVE_WIDTH = 1 << 1;

    /**
     * When present, the data points for the path are drawn with their respective control points
     *
     * @deprecated This flag is no longer supported
     */
    @Deprecated
    public static final int FLAG_DEBUG = Integer.MIN_VALUE;


    // constants
    static final float THRESHOLD_VELOCITY = 7f;         // in/s
    static final float THRESHOLD_ACCELERATION = 3f;    // in/s^2
    static final float FILTER_RATIO_MIN = 0.22f;
    static final float FILTER_RATIO_ACCELERATION_MODIFIER = 0.1f;
    static final int DEFAULT_FLAGS = FLAG_INTERPOLATION | FLAG_RESPONSIVE_WIDTH;
    static final int DEFAULT_STROKE_COLOR = 0xFF000000;

    // settings
    int flags;
    float maxStrokeWidth;
    float minStrokeWidth;
    float smoothingRatio;

    // points
    List<InkPoint> pointQueue = new ArrayList<>();
    List<InkPoint> pointRecycle = new ArrayList<>();

    // misc
    float density;
    Bitmap bitmap;
    Canvas canvas;
    Paint paint;
    RectF dirty;
    ArrayList<InkListener> listeners = new ArrayList<>();

    private boolean isEmpty;

    public InkView(Context context) {
        this(context, DEFAULT_FLAGS);
    }

    public InkView(Context context, int flags) {
        super(context);

        init(flags);
    }

    public InkView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InkView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // get flags from attributes
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.InkView, defStyleAttr, 0);
        int flags = a.getInt(R.styleable.InkView_inkFlags, DEFAULT_FLAGS);
        a.recycle();

        init(flags);
    }

    private void init(int flags) {
        // init flags
        setFlags(flags);

        // init screen density
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        density = (metrics.xdpi + metrics.ydpi) / 2f;

        // init paint
        paint = new Paint();
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setAntiAlias(true);

        // apply default settings
        setColor(DEFAULT_STROKE_COLOR);
        setMaxStrokeWidth(DEFAULT_MAX_STROKE_WIDTH);
        setMinStrokeWidth(DEFAULT_MIN_STROKE_WIDTH);
        setSmoothingRatio(DEFAULT_SMOOTHING_RATIO);

        // init dirty rect
        dirty = new RectF();

        isEmpty = true;
    }


    //--------------------------------------
    // Events
    //--------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        clear();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getAction();
        isEmpty = false;
        // on down, initialize stroke point
        if (action == MotionEvent.ACTION_DOWN) {
            addPoint(getRecycledPoint(e.getX(), e.getY(), e.getEventTime()));

            // notify listeners of sign
            for (InkListener listener : listeners) {
                listener.onInkDraw();
            }
        }

        // on move, add next point
        else if (action == MotionEvent.ACTION_MOVE) {
            if (!pointQueue.get(pointQueue.size() - 1).equals(e.getX(), e.getY())) {
                addPoint(getRecycledPoint(e.getX(), e.getY(), e.getEventTime()));
            }
        }

        // on up, draw remaining queue
        if (action == MotionEvent.ACTION_UP) {
            // draw final points
            if (pointQueue.size() == 1) {
                draw(pointQueue.get(0));
            } else if (pointQueue.size() == 2) {
                pointQueue.get(1).findControlPoints(pointQueue.get(0), null);
                draw(pointQueue.get(0), pointQueue.get(1));
            }

            // recycle remaining points
            pointRecycle.addAll(pointQueue);
            pointQueue.clear();
        }

        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // simply paint the bitmap on the canvas
        canvas.drawBitmap(bitmap, 0, 0, null);

        super.onDraw(canvas);
    }


    //--------------------------------------
    // Public Methods
    //--------------------------------------

    /**
     * Sets the feature flags for the view. This will overwrite any previously set flag
     *
     * @param flags A bit mask of one or more flags (ie. FLAG_INTERPOLATION | FLAG_RESPONSIVE_WIDTH)
     */
    public void setFlags(int flags) {
        this.flags = flags;
    }

    /**
     * Adds the feature flag(s) to the view.
     *
     * @param flags A bit mask of one or more flags (ie. FLAG_INTERPOLATION | FLAG_RESPONSIVE_WIDTH)
     */
    public void addFlags(int flags) {
        this.flags |= flags;
    }

    /**
     * Alias for {@link #addFlags(int) addFlags}
     *
     * @param flag A feature flag (ie. FLAG_INTERPOLATION)
     */
    public void addFlag(int flag) {
        addFlags(flag);
    }

    /**
     * Removes the feature flag(s) from the view.
     *
     * @param flags A bit mask of one or more flags (ie. FLAG_INTERPOLATION | FLAG_RESPONSIVE_WIDTH)
     */
    public void removeFlags(int flags) {
        this.flags &= ~flags;
    }

    /**
     * Alias for {@link #removeFlags(int) removeFlags}
     *
     * @param flag A feature flag (ie. FLAG_INTERPOLATION)
     */
    public void removeFlag(int flag) {
        removeFlags(flag);
    }

    /**
     * Checks to see if the view has the supplied flag(s)
     *
     * @param flags A bit mask of one or more flags (ie. FLAG_INTERPOLATION | FLAG_RESPONSIVE_WIDTH)
     * @return True or False
     */
    public boolean hasFlags(int flags) {
        return (this.flags & flags) > 0;
    }

    /**
     * Alias for {@link #hasFlags(int flags) hasFlags}
     *
     * @param flag A feature flag (ie. FLAG_INTERPOLATION)
     * @return True or False
     */
    public boolean hasFlag(int flag) {
        return hasFlags(flag);
    }

    /**
     * Clears all feature flags from the view
     */
    public void clearFlags() {
        flags = 0;
    }

    /**
     * Adds a listener on the view
     *
     * @param listener The listener
     */
    public void addListener(InkListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Adds a listener on the view
     *
     * @param listener The listener
     * @deprecated Use {@link #addListener(InkListener listener)} instead
     */
    @Deprecated
    public void addInkListener(InkListener listener) {
        addListener(listener);
    }

    /**
     * Removes the listener from the view
     *
     * @param listener The listener
     */
    public void removeListener(InkListener listener) {
        listeners.remove(listener);
    }

    /**
     * Removes the listener from the view
     *
     * @param listener The listener
     * @deprecated Use {@link #removeListener(InkListener listener)} instead
     */
    @Deprecated
    public void removeInkListener(InkListener listener) {
        removeListener(listener);
    }

    /**
     * Sets the stroke color
     *
     * @param color The color value
     */
    public void setColor(int color) {
        paint.setColor(color);
    }

    /**
     * Sets the maximum stroke width
     *
     * @param width The width (in dp)
     */
    public void setMaxStrokeWidth(float width) {
        maxStrokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, width, getResources().getDisplayMetrics());
    }

    /**
     * Sets the minimum stroke width
     *
     * @param width The width (in dp)
     */
    public void setMinStrokeWidth(float width) {
        minStrokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, width, getResources().getDisplayMetrics());
    }

    /**
     * Returns the smoothing ratio
     *
     * @return The smoothing ratio
     */
    public float getSmoothingRatio() {
        return smoothingRatio;
    }

    /**
     * Sets the smoothing ratio for calculating control points.
     * This value is ignored when the FLAG_INTERPOLATING is removed
     *
     * @param ratio The smoothing ratio, between 0 and 1
     */
    public void setSmoothingRatio(float ratio) {
        smoothingRatio = Math.max(Math.min(ratio, 1f), 0f);
    }

    /**
     * Checks if the view is empty
     *
     * @return True of False
     */
    public boolean isViewEmpty() {
        return isEmpty;
    }

    /**
     * Clears the view
     */
    public void clear() {
        // clean up existing bitmap
        if (bitmap != null) {
            bitmap.recycle();
        }

        // init bitmap cache
        bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);

        // notify listeners
        for (InkListener listener : listeners) {
            listener.onInkClear();
        }

        invalidate();
        isEmpty = true;
    }

    /**
     * Returns the bitmap of the drawing with a transparent background
     *
     * @return The bitmap
     */
    public Bitmap getBitmap() {
        return getBitmap(0);
    }

    /**
     * Returns the bitmap of the drawing with the specified background color
     *
     * @param backgroundColor The background color for the bitmap
     * @return The bitmap
     */
    public Bitmap getBitmap(int backgroundColor) {
        // create new bitmap
        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas bitmapCanvas = new Canvas(bitmap);

        // draw background if not transparent
        if (backgroundColor != 0) {
            bitmapCanvas.drawColor(backgroundColor);
        }

        // draw bitmap
        bitmapCanvas.drawBitmap(this.bitmap, 0, 0, null);

        return bitmap;
    }

    /**
     * Draws a bitmap to the view, with its top left corner at (x,y)
     *
     * @param bitmap The bitmap to draw
     * @param x      The destination x coordinate of the bitmap in relation to the view
     * @param y      The destination y coordinate of the bitmap in relation to the view
     * @param paint  The paint used to draw the bitmap (may be null)
     */
    public void drawBitmap(Bitmap bitmap, float x, float y, Paint paint) {
        canvas.drawBitmap(bitmap, x, y, paint);

        invalidate();
    }


    //--------------------------------------
    // Listener Interfaces
    //--------------------------------------

    /**
     * Listener for the ink view to notify on actions
     */
    public interface InkListener {
        /**
         * Callback method when the ink view has been cleared
         */
        void onInkClear();

        /**
         * Callback method when the ink view receives a touch event
         * (Will be fired multiple times during a signing)
         */
        void onInkDraw();
    }


    //--------------------------------------
    // Util
    //--------------------------------------

    float getDensity() {
        return density;
    }

    void addPoint(InkPoint p) {
        pointQueue.add(p);

        int queueSize = pointQueue.size();
        if (queueSize == 1) {
            // compute starting velocity
            int recycleSize = pointRecycle.size();
            p.velocity = (recycleSize > 0) ? pointRecycle.get(recycleSize - 1).velocityTo(p) / 2f : 0f;

            // compute starting stroke width
            paint.setStrokeWidth(computeStrokeWidth(p.velocity));
        } else if (queueSize == 2) {
            InkPoint p0 = pointQueue.get(0);

            // compute velocity for new point
            p.velocity = p0.velocityTo(p);

            // re-compute velocity for 1st point (predictive velocity)
            p0.velocity = p0.velocity + p.velocity / 2f;

            // find control points for first point
            p0.findControlPoints(null, p);

            // update starting stroke width
            paint.setStrokeWidth(computeStrokeWidth(p0.velocity));
        } else if (queueSize == 3) {
            InkPoint p0 = pointQueue.get(0);
            InkPoint p1 = pointQueue.get(1);

            // find control points for second point
            p1.findControlPoints(p0, p);

            // compute velocity for new point
            p.velocity = p1.velocityTo(p);

            // draw geometry between first 2 points
            draw(p0, p1);

            // recycle 1st point
            pointRecycle.add(pointQueue.remove(0));
        }
    }

    InkPoint getRecycledPoint(float x, float y, long time) {
        if (pointRecycle.size() == 0) {
            return new InkPoint(x, y, time, density, smoothingRatio);
        }

        return pointRecycle.remove(0).reset(x, y, time);
    }

    float computeStrokeWidth(float velocity) {
        // compute responsive width
        if (hasFlags(FLAG_RESPONSIVE_WIDTH)) {
            return maxStrokeWidth - (maxStrokeWidth - minStrokeWidth) * Math.min(velocity / THRESHOLD_VELOCITY, 1f);
        }

        return maxStrokeWidth;
    }

    void draw(InkPoint p) {
        paint.setStyle(Paint.Style.FILL);

        // draw dot
        canvas.drawCircle(p.x, p.y, paint.getStrokeWidth() / 2f, paint);

        invalidate();
    }

    void draw(InkPoint p1, InkPoint p2) {
        // init dirty rect
        dirty.left = Math.min(p1.x, p2.x);
        dirty.right = Math.max(p1.x, p2.x);
        dirty.top = Math.min(p1.y, p2.y);
        dirty.bottom = Math.max(p1.y, p2.y);

        paint.setStyle(Paint.Style.STROKE);

        // adjust low-pass ratio from changing acceleration
        // using comfortable range of 0.2 -> 0.3 approx.
        float acceleration = Math.abs((p2.velocity - p1.velocity) / (p2.time - p1.time)); // in/s^2
        float filterRatio = Math.min(FILTER_RATIO_MIN + FILTER_RATIO_ACCELERATION_MODIFIER * acceleration / THRESHOLD_ACCELERATION, 1f);

        // compute new stroke width
        float desiredWidth = computeStrokeWidth(p2.velocity);
        float startWidth = paint.getStrokeWidth();

        float endWidth = filterRatio * desiredWidth + (1f - filterRatio) * startWidth;
        float deltaWidth = endWidth - startWidth;

        // interpolate bezier curve
        if (hasFlags(FLAG_INTERPOLATION)) {

            // compute # of steps to interpolate in the bezier curve
            int steps = (int) (Math.sqrt(Math.pow(p2.x - p1.x, 2) + Math.pow(p2.y - p1.y, 2)) / 5);

            // computational setup for differentials used to interpolate the bezier curve
            float u = 1f / (steps + 1);
            float uu = u * u;
            float uuu = u * u * u;

            float pre1 = 3f * u;
            float pre2 = 3f * uu;
            float pre3 = 6f * uu;
            float pre4 = 6f * uuu;

            float tmp1x = p1.x - p1.c2x * 2f + p2.c1x;
            float tmp1y = p1.y - p1.c2y * 2f + p2.c1y;
            float tmp2x = (p1.c2x - p2.c1x) * 3f - p1.x + p2.x;
            float tmp2y = (p1.c2y - p2.c1y) * 3f - p1.y + p2.y;

            float dx = (p1.c2x - p1.x) * pre1 + tmp1x * pre2 + tmp2x * uuu;
            float dy = (p1.c2y - p1.y) * pre1 + tmp1y * pre2 + tmp2y * uuu;
            float ddx = tmp1x * pre3 + tmp2x * pre4;
            float ddy = tmp1y * pre3 + tmp2y * pre4;
            float dddx = tmp2x * pre4;
            float dddy = tmp2y * pre4;

            float x1 = p1.x;
            float y1 = p1.y;
            float x2, y2;

            // iterate over each step and draw the curve
            int i = 0;
            while (i++ < steps) {
                x2 = x1 + dx;
                y2 = y1 + dy;

                paint.setStrokeWidth(startWidth + deltaWidth * i / steps);
                canvas.drawLine(x1, y1, x2, y2, paint);

                x1 = x2;
                y1 = y2;
                dx += ddx;
                dy += ddy;
                ddx += dddx;
                ddy += dddy;

                // adjust dirty bounds to account for curve
                dirty.left = Math.min(dirty.left, x1);
                dirty.right = Math.max(dirty.right, x1);
                dirty.top = Math.min(dirty.top, y1);
                dirty.bottom = Math.max(dirty.bottom, y1);
            }

            paint.setStrokeWidth(endWidth);
            canvas.drawLine(x1, y1, p2.x, p2.y, paint);
        }
        // no interpolation, draw line between points
        else {
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint);
            paint.setStrokeWidth(endWidth);
        }

        invalidate((int) (dirty.left - maxStrokeWidth / 2), (int) (dirty.top - maxStrokeWidth / 2), (int) (dirty.right + maxStrokeWidth / 2), (int) (dirty.bottom + maxStrokeWidth / 2));
    }

    /**
     * Returns the points of the drawing with the specified background color
     *
     * @return The points list
     */
    public List<InkPoint> getPoints() {
        return this.pointQueue;
    }
}
