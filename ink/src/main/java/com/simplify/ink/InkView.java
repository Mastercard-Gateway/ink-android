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


public class InkView extends View
{
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
     */
    public static final int FLAG_DEBUG = 1 << 2;


    // constants
    private static final float THRESHOLD_VELOCITY = 7f;         // in/s
    private static final float THRESHOLD_ACCELERATION = 3f;    // in/s^2
    private static final float FILTER_RATIO_MIN = 0.22f;
    private static final float FILTER_RATIO_ACCEL_MOD = 0.1f;
    private static final int DEFAULT_FLAGS = FLAG_INTERPOLATION | FLAG_RESPONSIVE_WIDTH;

    // settings
    private int mFlags;
    private float mMaxStrokeWidth;
    private float mMinStrokeWidth;
    private float mSmoothingRatio;

    // points
    private ArrayList<InkPoint> mPointQueue = new ArrayList<InkPoint>();
    private ArrayList<InkPoint> mPointRecycle = new ArrayList<InkPoint>();

    // misc
    private float mDensity;
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private Paint mPaint;
    private RectF mDirty;
    private ArrayList<InkListener> mListeners = new ArrayList<InkListener>();

    // debug
    private boolean mHasDebugLayer = false;
    private Bitmap mDebugBitmap;
    private Canvas mDebugCanvas;
    private Paint mDebugPointPaint;
    private Paint mDebugControlPaint;
    private Paint mDebugLinePaint;


    public InkView(Context context)
    {
        this(context, DEFAULT_FLAGS);
    }

    public InkView(Context context, int flags)
    {
        super(context);

        init(flags);
    }

    public InkView(Context context, AttributeSet attrs)
    {
        this(context, attrs, 0);
    }

    public InkView(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);

        // get flags from attributes
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.InkView, defStyleAttr, 0);
        int flags = a.getInt(R.styleable.InkView_inkFlags, DEFAULT_FLAGS);
        a.recycle();

        init(flags);
    }

    private void init(int flags)
    {
        // init flags
        setFlags(flags);

        // init screen density
        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        mDensity = (metrics.xdpi + metrics.ydpi) / 2f;

        // init paint
        mPaint = new Paint();
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setAntiAlias(true);

        // init debug paint
        mDebugPointPaint = new Paint();
        mDebugPointPaint.setAntiAlias(true);
        mDebugPointPaint.setStyle(Paint.Style.FILL);
        mDebugPointPaint.setColor(getContext().getResources().getColor(android.R.color.holo_red_dark));
        mDebugControlPaint = new Paint();
        mDebugControlPaint.setAntiAlias(true);
        mDebugControlPaint.setStyle(Paint.Style.FILL);
        mDebugControlPaint.setColor(getContext().getResources().getColor(android.R.color.holo_blue_dark));
        mDebugLinePaint = new Paint();
        mDebugLinePaint.setAntiAlias(true);
        mDebugLinePaint.setStyle(Paint.Style.STROKE);
        mDebugLinePaint.setColor(getContext().getResources().getColor(android.R.color.darker_gray));

        // apply default settings
        setColor(getResources().getColor(android.R.color.black));
        setMaxStrokeWidth(DEFAULT_MAX_STROKE_WIDTH);
        setMinStrokeWidth(DEFAULT_MIN_STROKE_WIDTH);
        setSmoothingRatio(DEFAULT_SMOOTHING_RATIO);

        // init dirty rect
        mDirty = new RectF();
    }


    //--------------------------------------
    // Events
    //--------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh)
    {
        super.onSizeChanged(w, h, oldw, oldh);

        clear();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e)
    {
        int action = e.getAction();

        // on down, initialize stroke point
        if (action == MotionEvent.ACTION_DOWN) {
            addPoint(getRecycledPoint(e.getX(), e.getY(), e.getEventTime()));

            // notify listeners of sign
            for (InkListener listener : mListeners) {
                listener.onInkDraw();
            }
        }

        // on move, add next point
        else if (action == MotionEvent.ACTION_MOVE) {
            if (!mPointQueue.get(mPointQueue.size() - 1).equals(e.getX(), e.getY())) {
                addPoint(getRecycledPoint(e.getX(), e.getY(), e.getEventTime()));
            }
        }

        // on up, draw remaining queue
        if (action == MotionEvent.ACTION_UP) {
            // draw final points
            if (mPointQueue.size() == 1) {
                draw(mPointQueue.get(0));
            }
            else if (mPointQueue.size() == 2) {
                mPointQueue.get(1).findControlPoints(mPointQueue.get(0), null);
                draw(mPointQueue.get(0), mPointQueue.get(1));
            }

            // recycle remaining points
            mPointRecycle.addAll(mPointQueue);
            mPointQueue.clear();
        }

        return true;
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        // simply paint the bitmap on the canvas
        canvas.drawBitmap(mBitmap, 0, 0, null);

        // draw debug layer if it has some data
        if (mHasDebugLayer) {
            canvas.drawBitmap(mDebugBitmap, 0, 0, null);
        }

        super.onDraw(canvas);
    }


    //--------------------------------------
    // Public Methods
    //--------------------------------------

    /**
     * Sets the feature flags for the view. This will overwrite any previously set flag
     * @param flags A bit mask of one or more flags (ie. FLAG_INTERPOLATION | FLAG_RESPONSIVE_WIDTH)
     */
    public void setFlags(int flags)
    {
        mFlags = flags;
    }

    /**
     * Adds the feature flag(s) to the view.
     * @param flags A bit mask of one or more flags (ie. FLAG_INTERPOLATION | FLAG_RESPONSIVE_WIDTH)
     */
    public void addFlags(int flags)
    {
        mFlags |= flags;
    }

    /**
     * Alias for {@link #addFlags(int) addFlags}
     * @param flag A feature flag (ie. FLAG_INTERPOLATION)
     */
    public void addFlag(int flag)
    {
        addFlags(flag);
    }

    /**
     * Removes the feature flag(s) from the view.
     * @param flags A bit mask of one or more flags (ie. FLAG_INTERPOLATION | FLAG_RESPONSIVE_WIDTH)
     */
    public void removeFlags(int flags)
    {
        mFlags &= ~flags;
    }

    /**
     * Alias for {@link #removeFlags(int) removeFlags}
     * @param flag A feature flag (ie. FLAG_INTERPOLATION)
     */
    public void removeFlag(int flag)
    {
        removeFlags(flag);
    }

    /**
     * Checks to see if the view has the supplied flag(s)
     * @param flags A bit mask of one or more flags (ie. FLAG_INTERPOLATION | FLAG_RESPONSIVE_WIDTH)
     * @return True or False
     */
    public boolean hasFlags(int flags)
    {
        return (mFlags & flags) > 0;
    }

    /**
     * Alias for {@link #hasFlags(int flags) hasFlags}
     * @param flag A feature flag (ie. FLAG_INTERPOLATION)
     * @return True or False
     */
    public boolean hasFlag(int flag)
    {
        return hasFlags(flag);
    }

    /**
     * Clears all feature flags from the view
     */
    public void clearFlags()
    {
        mFlags = 0;
    }

    /**
     * Sets a ink listener on the view
     * @param listener The listener
     */
    public void addInkListener(InkListener listener)
    {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    /**
     * Removes the registered ink listener from the view
     * @param listener The listener
     */
    public void removeInkListener(InkListener listener)
    {
        mListeners.remove(listener);
    }

    /**
     * Sets the ink color
     * @param color The color value
     */
    public void setColor(int color)
    {
        mPaint.setColor(color);
    }

    /**
     * Sets the maximum stroke width
     * @param width The width (in dp)
     */
    public void setMaxStrokeWidth(float width)
    {
        mMaxStrokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, width, getResources().getDisplayMetrics());
    }

    /**
     * Sets the minimum stroke width
     * @param width The width (in dp)
     */
    public void setMinStrokeWidth(float width)
    {
        mMinStrokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, width, getResources().getDisplayMetrics());
    }

    /**
     * Returns the smoothing ratio
     * @return The smoothing ratio
     */
    public float getSmoothingRatio()
    {
        return mSmoothingRatio;
    }

    /**
     * Sets the smoothing ratio for calculating control points.
     * This value is ignored when the FLAG_INTERPOLATING is removed
     * @param ratio The smoothing ratio, between 0 and 1
     */
    public void setSmoothingRatio(float ratio)
    {
        mSmoothingRatio = Math.max(Math.min(ratio, 1f), 0f);
    }

    /**
     * Clears the view
     */
    public void clear()
    {
        // clean up existing bitmap
        if (mBitmap != null) {
            mBitmap.recycle();
        }

        // cleanup debug bitmap
        if (mDebugBitmap != null) {
            mDebugBitmap.recycle();
        }

        // init bitmap cache
        mBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mBitmap);

        // init debug bitmap cache
        mDebugBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        mDebugCanvas = new Canvas(mDebugBitmap);
        mHasDebugLayer = false;

        // notify listeners
        for (InkListener listener : mListeners) {
            listener.onInkClear();
        }

        invalidate();
    }

    /**
     * Returns the bitmap of the drawing with a transparent background
     * @return The bitmap
     */
    public Bitmap getBitmap()
    {
        return getBitmap(0);
    }

    /**
     * Returns the bitmap of the drawing with the specified background color
     * @param backgroundColor The background color for the bitmap
     * @return The bitmap
     */
    public Bitmap getBitmap(int backgroundColor)
    {
        // create new bitmap
        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas bitmapCanvas = new Canvas(bitmap);

        // draw background if not transparent
        if (backgroundColor != 0) {
            bitmapCanvas.drawColor(backgroundColor);
        }

        // draw bitmap
        bitmapCanvas.drawBitmap(mBitmap, 0, 0, null);

        return bitmap;
    }

    /**
     * Draws a bitmap to the view, with its top left corner at (x,y)
     * @param bitmap The bitmap to draw
     * @param x      The destination x coordinate of the bitmap in relation to the view
     * @param y      The destination y coordinate of the bitmap in relation to the view
     * @param paint  The paint used to draw the bitmap (may be null)
     */
    public void drawBitmap(Bitmap bitmap, float x, float y, Paint paint)
    {
        mCanvas.drawBitmap(bitmap, x, y, paint);

        invalidate();
    }


    //--------------------------------------
    // Util
    //--------------------------------------

    float getDensity()
    {
        return mDensity;
    }

    void addPoint(InkPoint p)
    {
        mPointQueue.add(p);

        int queueSize = mPointQueue.size();
        if (queueSize == 1) {
            // compute starting velocity
            int recycleSize = mPointRecycle.size();
            p.velocity = (recycleSize > 0) ? mPointRecycle.get(recycleSize - 1).velocityTo(p) / 2f : 0f;

            // compute starting stroke width
            mPaint.setStrokeWidth(computeStrokeWidth(p.velocity));
        }
        if (queueSize == 2) {
            InkPoint p0 = mPointQueue.get(0);

            // compute velocity for new point
            p.velocity = p0.velocityTo(p);

            // re-compute velocity for 1st point (predictive velocity)
            p0.velocity = p0.velocity + p.velocity / 2f;

            // find control points for first point
            p0.findControlPoints(null, p);

            // update starting stroke width
            mPaint.setStrokeWidth(computeStrokeWidth(p0.velocity));
        }
        else if (queueSize == 3) {
            InkPoint p0 = mPointQueue.get(0);
            InkPoint p1 = mPointQueue.get(1);

            // find control points for second point
            p1.findControlPoints(p0, p);

            // compute velocity for new point
            p.velocity = p1.velocityTo(p);

            // draw geometry between first 2 points
            draw(p0, p1);

            // recycle 1st point
            mPointRecycle.add(mPointQueue.remove(0));
        }
    }

    InkPoint getRecycledPoint(float x, float y, long time)
    {
        if (mPointRecycle.size() == 0) {
            return new InkPoint(x, y, time);
        }

        return mPointRecycle.remove(0).reset(x, y, time);
    }

    float computeStrokeWidth(float velocity)
    {
        // compute responsive width
        if (hasFlags(FLAG_RESPONSIVE_WIDTH)) {
            return mMaxStrokeWidth - (mMaxStrokeWidth - mMinStrokeWidth) * Math.min(velocity / THRESHOLD_VELOCITY, 1f);
        }

        return mMaxStrokeWidth;
    }

    void draw(InkPoint p)
    {
        mPaint.setStyle(Paint.Style.FILL);

        // draw dot
        mCanvas.drawCircle(p.x, p.y, mPaint.getStrokeWidth() / 2f, mPaint);

        invalidate();
    }

    void draw(InkPoint p1, InkPoint p2)
    {
        // init dirty rect
        mDirty.left = Math.min(p1.x, p2.x);
        mDirty.right = Math.max(p1.x, p2.x);
        mDirty.top = Math.min(p1.y, p2.y);
        mDirty.bottom = Math.max(p1.y, p2.y);

        mPaint.setStyle(Paint.Style.STROKE);

        // adjust low-pass ratio from changing acceleration
        // using comfortable range of 0.2 -> 0.3 approx.
        float acceleration = Math.abs((p2.velocity - p1.velocity) / (p2.time - p1.time)); // in/s^2
        float filterRatio = Math.min(FILTER_RATIO_MIN + FILTER_RATIO_ACCEL_MOD * acceleration / THRESHOLD_ACCELERATION, 1f);

        // compute new stroke width
        float desiredWidth = computeStrokeWidth(p2.velocity);
        float startWidth = mPaint.getStrokeWidth();

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

                mPaint.setStrokeWidth(startWidth + deltaWidth * i / steps);
                mCanvas.drawLine(x1, y1, x2, y2, mPaint);

                x1 = x2;
                y1 = y2;
                dx += ddx;
                dy += ddy;
                ddx += dddx;
                ddy += dddy;

                // adjust dirty bounds to account for curve
                mDirty.left = Math.min(mDirty.left, x1);
                mDirty.right = Math.max(mDirty.right, x1);
                mDirty.top = Math.min(mDirty.top, y1);
                mDirty.bottom = Math.max(mDirty.bottom, y1);
            }

            mPaint.setStrokeWidth(endWidth);
            mCanvas.drawLine(x1, y1, p2.x, p2.y, mPaint);
        }
        // no interpolation, draw line between points
        else {
            mCanvas.drawLine(p1.x, p1.y, p2.x, p2.y, mPaint);
            mPaint.setStrokeWidth(endWidth);
        }

        // draw debug layer
        if (hasFlags(FLAG_DEBUG)) {

            // draw control points if interpolating
            if (hasFlags(FLAG_INTERPOLATION)) {
                float controlRadius = mMaxStrokeWidth / 3f;

                mDebugCanvas.drawLine(p1.c1x, p1.c1y, p1.c2x, p1.c2y, mDebugLinePaint);
                mDebugCanvas.drawLine(p2.c1x, p2.c1y, p2.c2x, p2.c2y, mDebugLinePaint);
                mDebugCanvas.drawCircle(p1.c1x, p1.c1y, controlRadius, mDebugControlPaint);
                mDebugCanvas.drawCircle(p1.c2x, p1.c2y, controlRadius, mDebugControlPaint);
                mDebugCanvas.drawCircle(p2.c1x, p2.c1y, controlRadius, mDebugControlPaint);
                mDebugCanvas.drawCircle(p2.c2x, p2.c2y, controlRadius, mDebugControlPaint);

                // TODO adjust dirty bounds to account for control points
            }

            float pointRadius = mMaxStrokeWidth / 1.5f;

            mDebugCanvas.drawCircle(p1.x, p1.y, pointRadius, mDebugPointPaint);
            mDebugCanvas.drawCircle(p2.x, p2.y, pointRadius, mDebugPointPaint);

            mHasDebugLayer = true;
        }

        invalidate((int) (mDirty.left - mMaxStrokeWidth / 2), (int) (mDirty.top - mMaxStrokeWidth / 2), (int) (mDirty.right + mMaxStrokeWidth / 2), (int) (mDirty.bottom + mMaxStrokeWidth / 2));
    }


    //--------------------------------------
    // Listener Interfaces
    //--------------------------------------

    /**
     * Listener for the ink view to notify on actions
     */
    public interface InkListener
    {
        /**
         * Callback method when the ink view has been cleared
         */
        public void onInkClear();

        /**
         * Callback method when the ink view receives a touch event
         * (Will be fired multiple times during a signing)
         */
        public void onInkDraw();
    }


    //--------------------------------------
    // Util Classes
    //--------------------------------------

    public class InkPoint
    {
        public float x, y, c1x, c1y, c2x, c2y, velocity;
        public long time;

        public InkPoint()
        {
        }

        public InkPoint(float x, float y, long time)
        {
            reset(x, y, time);
        }

        public InkPoint reset(float x, float y, long time)
        {
            this.x = x;
            this.y = y;
            this.time = time;
            velocity = 0f;

            c1x = x;
            c1y = y;
            c2x = x;
            c2y = y;

            return this;
        }

        public boolean equals(InkPoint p)
        {
            return equals(p.x, p.y);
        }

        public boolean equals(float x, float y)
        {
            return this.x == x && this.y == y;
        }

        public float distanceTo(InkPoint p)
        {
            float dx = p.x - x;
            float dy = p.y - y;

            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        public float velocityTo(InkPoint p)
        {
            return (1000f * distanceTo(p)) / (Math.abs(p.time - time) * getDensity()); // in/s
        }

        public void findControlPoints(InkPoint prev, InkPoint next)
        {
            if (prev == null && next == null) {
                return;
            }

            float r = getSmoothingRatio();

            // if start of a stroke, c2 control points half-way between this and next point
            if (prev == null) {
                c2x = x + r * (next.x - x) / 2f;
                c2y = y + r * (next.y - y) / 2f;
                return;
            }

            // if end of a stroke, c1 control points half-way between this and prev point
            if (next == null) {
                c1x = x + r * (prev.x - x) / 2f;
                c1y = y + r * (prev.y - y) / 2f;
                return;
            }

            // init control points
            c1x = (x + prev.x) / 2f;
            c1y = (y + prev.y) / 2f;
            c2x = (x + next.x) / 2f;
            c2y = (y + next.y) / 2f;

            // calculate control offsets
            float len1 = distanceTo(prev);
            float len2 = distanceTo(next);
            float k = len1 / (len1 + len2);
            float xM = c1x + (c2x - c1x) * k;
            float yM = c1y + (c2y - c1y) * k;
            float dx = x - xM;
            float dy = y - yM;

            // inverse smoothing ratio
            r = 1f - r;

            // translate control points
            c1x += dx + r * (xM - c1x);
            c1y += dy + r * (yM - c1y);
            c2x += dx + r * (xM - c2x);
            c2y += dy + r * (yM - c2y);
        }
    }
}
