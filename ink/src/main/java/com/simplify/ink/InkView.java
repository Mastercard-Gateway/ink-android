package com.simplify.ink;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;


public class InkView extends View
{
    public enum Mode { NORMAL, DEBUG }

    // defaults
    public static final float DEFAULT_MAX_STROKE_WIDTH = 5f;
    public static final float DEFAULT_MIN_STROKE_WIDTH = 1.5f;
    public static final float DEFAULT_SMOOTHING_RATIO = 0.75f;

    // constants
    private static final float THRESHOLD_VELOCITY = 6f;         // in/s
    private static final float THRESHOLD_ACCELERATION = 10f;    // in/s^2
    private static final float FILTER_RATIO_MIN = 0.2f;
    private static final float FILTER_RATIO_ACCEL_MOD = 0.1f;

    // settings
    private Mode mMode = Mode.NORMAL;
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
    private ArrayList<SignatureListener> mListeners = new ArrayList<SignatureListener>();

    // debug
    private Bitmap mDebugBitmap;
    private Canvas mDebugCanvas;
    private Paint mDebugPointPaint;
    private Paint mDebugControlPaint;
    private Paint mDebugLinePaint;


    public InkView(Context context)
    {
        super(context);
        init();
    }

    public InkView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        init();
    }

    public InkView(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init()
    {
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
            for (SignatureListener listener : mListeners) {
                listener.onSignatureWrite();
            }
        }

        // on move, add next point
        else if (action == MotionEvent.ACTION_MOVE) {
            addPoint(getRecycledPoint(e.getX(), e.getY(), e.getEventTime()));
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

        // draw debug layer
        if (mMode == Mode.DEBUG) {
            canvas.drawBitmap(mDebugBitmap, 0, 0, null);
        }

        super.onDraw(canvas);
    }


    //--------------------------------------
    // Public Methods
    //--------------------------------------

    public Mode getMode()
    {
        return mMode;
    }

    public void setMode(Mode mode)
    {
        mMode = mode;
        clear();
    }

    /**
     * Sets a signature listener on the view
     *
     * @param listener The listener
     */
    public void addSignatureListener(SignatureListener listener)
    {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    /**
     * Removes the registered signature listener from the view
     */
    public void removeSignatureListener(SignatureListener listener)
    {
        mListeners.remove(listener);
    }

    /**
     * Sets the ink color
     *
     * @param color The color value
     */
    public void setColor(int color)
    {
        mPaint.setColor(color);
    }

    /**
     * Sets the maximum stroke width
     *
     * @param width The width (in dp)
     */
    public void setMaxStrokeWidth(float width)
    {
        mMaxStrokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, width, getResources().getDisplayMetrics());
    }

    /**
     * Sets the minumum stroke width
     *
     * @param width The width (in dp)
     */
    public void setMinStrokeWidth(float width)
    {
        mMinStrokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, width, getResources().getDisplayMetrics());
    }

    public float getSmoothingRatio()
    {
        return mSmoothingRatio;
    }

    public void setSmoothingRatio(float ratio)
    {
        mSmoothingRatio = Math.max(Math.min(ratio, 1f), 0f);
    }

    /**
     * Clears the current signature
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

        // init debug
        if (mMode == Mode.DEBUG) {
            mDebugBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            mDebugCanvas = new Canvas(mDebugBitmap);
        }

        // notify listeners
        for (SignatureListener listener : mListeners) {
            listener.onSignatureClear();
        }

        invalidate();
    }

    /**
     * Returns the bitmap of the signature with a transparent background
     * @return The bitmap
     */
    public Bitmap getBitmap()
    {
        return getBitmap(0);
    }

    /**
     * Returns the bitmap of the signature with the specified background color
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

        // draw signature bitmap
        bitmapCanvas.drawBitmap(mBitmap, 0, 0, null);

        return bitmap;
    }

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

    private void addPoint(InkPoint p)
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

    private InkPoint getRecycledPoint(float x, float y, long time)
    {
        if (mPointRecycle.size() == 0) {
            return new InkPoint(x, y, time);
        }

        return mPointRecycle.remove(0).reset(x, y, time);
    }

    private float computeStrokeWidth(float velocity)
    {
        return mMaxStrokeWidth - (mMaxStrokeWidth - mMinStrokeWidth) * Math.min(velocity / THRESHOLD_VELOCITY, 1f);
    }

    private void draw(InkPoint p)
    {
        mPaint.setStyle(Paint.Style.FILL);

        // draw dot
        mCanvas.drawCircle(p.x, p.y, mPaint.getStrokeWidth() / 2f, mPaint);

        invalidate();
    }

    private void draw(InkPoint p1, InkPoint p2)
    {
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
        }

        mPaint.setStrokeWidth(endWidth);
        mCanvas.drawLine(x1, y1, p2.x, p2.y, mPaint);

        // draw debug layer
        if (mMode == Mode.DEBUG) {
            mDebugCanvas.drawLine(p1.c1x, p1.c1y, p1.c2x, p1.c2y, mDebugLinePaint);
            mDebugCanvas.drawCircle(p1.c1x, p1.c1y, mMinStrokeWidth / 2f, mDebugControlPaint);
            mDebugCanvas.drawCircle(p1.c2x, p1.c2y, mMinStrokeWidth / 2f, mDebugControlPaint);
            mDebugCanvas.drawCircle(p1.x, p1.y, mMaxStrokeWidth / 2f, mDebugPointPaint);
            mDebugCanvas.drawLine(p2.c1x, p2.c1y, p2.c2x, p2.c2y, mDebugLinePaint);
            mDebugCanvas.drawCircle(p2.c1x, p2.c1y, mMinStrokeWidth / 2f, mDebugControlPaint);
            mDebugCanvas.drawCircle(p2.c2x, p2.c2y, mMinStrokeWidth / 2f, mDebugControlPaint);
            mDebugCanvas.drawCircle(p2.x, p2.y, mMaxStrokeWidth / 2f, mDebugPointPaint);
        }

        invalidate();
    }


    //--------------------------------------
    // Listener Interfaces
    //--------------------------------------

    /**
     * Listener for the signature view to notify on actions
     */
    public interface SignatureListener
    {
        /**
         * Callback method when the signature view has been cleared
         */
        public void onSignatureClear();

        /**
         * Callback method when the signature view receives a touch event
         * (Will be fired multiple times during a signing)
         */
        public void onSignatureWrite();
    }


    //--------------------------------------
    // Util Classes
    //--------------------------------------

    private class InkPoint
    {
        public float x, y, c1x, c1y, c2x, c2y, velocity;
        public long time;

        public InkPoint() {}

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
