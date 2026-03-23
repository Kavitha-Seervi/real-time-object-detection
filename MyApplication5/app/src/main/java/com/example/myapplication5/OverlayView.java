package com.example.myapplication5;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import java.util.List;

public class OverlayView extends View {

    private List<BoundingBox> results;
    private Paint boxPaint;
    private Paint textPaint;
    private Paint textBackgroundPaint;
    private Rect bounds;

    private static final int BOUNDING_RECT_TEXT_PADDING = 8;

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints(context);
        bounds = new Rect();
    }

    private void initPaints(Context context) {
        textBackgroundPaint = new Paint();
        textBackgroundPaint.setColor(Color.BLACK);
        textBackgroundPaint.setStyle(Paint.Style.FILL);
        textBackgroundPaint.setTextSize(50f);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(50f);

        boxPaint = new Paint();
        boxPaint.setColor(ContextCompat.getColor(context, R.color.bounding_box_color));
        boxPaint.setStrokeWidth(8f);
        boxPaint.setStyle(Paint.Style.STROKE);
    }

    public void clear() {
        if (results != null) {
            results.clear();
        }
        textPaint.reset();
        textBackgroundPaint.reset();
        boxPaint.reset();
        invalidate();
        initPaints(getContext());
    }

    public void setResults(List<BoundingBox> boundingBoxes) {
        this.results = boundingBoxes;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (results == null || results.isEmpty()) return;

        for (BoundingBox box : results) {
            float left = box.x1 * getWidth();
            float top = box.y1 * getHeight();
            float right = box.x2 * getWidth();
            float bottom = box.y2 * getHeight();

            // Draw bounding box
            canvas.drawRect(left, top, right, bottom, boxPaint);

            // Draw text
            String drawableText = box.clsName;

            textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length(), bounds);
            int textWidth = bounds.width();
            int textHeight = bounds.height();

            canvas.drawRect(
                    left,
                    top,
                    left + textWidth + BOUNDING_RECT_TEXT_PADDING,
                    top + textHeight + BOUNDING_RECT_TEXT_PADDING,
                    textBackgroundPaint
            );

            canvas.drawText(drawableText, left, top + textHeight, textPaint);
        }
    }
}