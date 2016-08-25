package hpbm.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

public class GaugeView extends View {

    private static final float MIN_VALUE_ANGLE = -75;
    private static final float MAX_VALUE_ANGLE = 75;
    private static final float ANGLE_RANGE = MAX_VALUE_ANGLE-MIN_VALUE_ANGLE;

    private static final float SCALE_IMAGE_WIDTH = 722;     // [px]
    private static final float SCALE_IMAGE_HEIGHT = 332;    // [px]
    private static final float SCALE_RING_WIDTH = 747;      // [px]
    private static final float SCALE_RING_HEIGHT = 747;     // [px]
    private static final float NEEDLE_IMAGE_WIDTH = 90;     // [px]
    private static final float NEEDLE_IMAGE_HEIGHT = 425;   // [px]
    private static final float SCALE_AXIS_X = 361;          // [px]
    private static final float SCALE_AXIS_Y = 373;          // [px]
    private static final float NEEDLE_AXIS_X = 45;          // [px]
    private static final float NEEDLE_AXIS_Y = 383;         // [px]

    private static final int SCALE_FILL_COLOR = Color.argb(255, 42, 169, 234);

    private Drawable mScaleDrawable;
    private Drawable mNeedleDrawable;

    private float value;

    public GaugeView(Context context) {
        super(context);
        init(null, 0);
    }

    public void setValue(float value) {
        this.value = value;
        invalidate();
    }

    public float getValue() {
        return value;
    }

    public GaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public GaugeView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    private void init(AttributeSet attrs, int defStyle) {
        mScaleDrawable = getContext().getDrawable(R.drawable.scale);
        mScaleDrawable.setCallback(this);
        mNeedleDrawable = getContext().getDrawable(R.drawable.needle);
        mNeedleDrawable.setCallback(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawScale( canvas );
        drawNeedle( canvas );
    }

    private void drawScale( Canvas canvas ) {
        int w = mScaleDrawable.getIntrinsicWidth();
        int h = mScaleDrawable.getIntrinsicHeight();
        float s = 1f*getWidth()/w;
        mScaleDrawable.setBounds( 0, 0, w, h );
        canvas.scale(s, s);
        canvas.translate(0, 40);
        mScaleDrawable.draw(canvas);

        Bitmap buffer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas bufferCanvas = new Canvas(buffer);
        bufferCanvas.drawARGB(0, 0, 0, 0);
        mScaleDrawable.draw(bufferCanvas);

        float ringSize = SCALE_RING_WIDTH/SCALE_IMAGE_WIDTH*w;
        float ringX = (w-ringSize)/2f;
        RectF rectF = new RectF( ringX, 0f, ringX+ringSize, ringSize );
        Paint paint = new Paint();
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        paint.setColor( SCALE_FILL_COLOR );
        if ( value > 0f ) {
            float startAngle = 0.5f*(180-ANGLE_RANGE);
            bufferCanvas.drawArc(rectF, -(startAngle + (1f - value) * ANGLE_RANGE), -ANGLE_RANGE * value, true, paint);
        }

        canvas.drawBitmap( buffer, 0, 0, null );
    }

    private void drawNeedle( Canvas canvas ) {
        float axisX = mScaleDrawable.getIntrinsicWidth()/2f;
        float axisY = mScaleDrawable.getIntrinsicHeight() * SCALE_AXIS_Y/SCALE_IMAGE_HEIGHT;
        int w = mNeedleDrawable.getIntrinsicWidth();
        int h = mNeedleDrawable.getIntrinsicHeight();
        canvas.translate( axisX, axisY );
        canvas.rotate( MIN_VALUE_ANGLE + value * ANGLE_RANGE );
        canvas.translate(-w*0.5f, -h*(NEEDLE_AXIS_Y/NEEDLE_IMAGE_HEIGHT));
        mNeedleDrawable.setBounds( 0, 0, w, h );
        mNeedleDrawable.draw(canvas);
    }

}
